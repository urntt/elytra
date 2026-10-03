package com.urntt.elytra.gametest;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.Connection;

/**
 * Sits first in the client's connection to a server and delays the raw data in both directions, to see the game as
 * it plays on a distant server. The delay applies in each direction, so a round trip takes twice as long. Data
 * always arrives in the order it was sent, also when the delay changes.
 *
 * <p>All handler methods run on the connection's event loop.
 */
@SuppressWarnings("UnstableApiUsage")
final class SimulatedLatency extends ChannelDuplexHandler {
	private static final String HANDLER_NAME = "elytra_gametest_latency";

	private final Channel channel;
	private final ArrayDeque<Delayed> inbound = new ArrayDeque<>();
	private final ArrayDeque<Delayed> outbound = new ArrayDeque<>();
	private final List<Runnable> held = new ArrayList<>();
	private long delayNanos;
	private boolean holding;

	private SimulatedLatency(final Channel channel) {
		this.channel = channel;
	}

	/**
	 * Returns the simulated latency of the client's current server connection, adding it without any delay first.
	 */
	static SimulatedLatency of(final ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Channel channel = channel(client.getConnection().getConnection());
			if (channel.pipeline().get(HANDLER_NAME) instanceof SimulatedLatency latency) {
				return latency;
			}
			SimulatedLatency latency = new SimulatedLatency(channel);
			channel.pipeline().addFirst(HANDLER_NAME, latency);
			return latency;
		});
	}

	private static Channel channel(final Connection connection) {
		try {
			Field channel = Connection.class.getDeclaredField("channel");
			channel.setAccessible(true);
			return (Channel) channel.get(connection);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("cannot reach the connection's channel", exception);
		}
	}

	/**
	 * Sets the delay in each direction, in milliseconds, for the data sent and received from now on.
	 */
	void setOneWayDelay(final int millis) {
		this.channel.eventLoop().execute(() -> this.delayNanos = TimeUnit.MILLISECONDS.toNanos(millis));
	}

	/**
	 * Holds back everything the client sends from now on until {@link #sendHeld()}, so that the server receives it
	 * all at once and handles several ticks' worth of the client's packets before its next tick.
	 */
	void holdSending() {
		this.channel.eventLoop().execute(() -> this.holding = true);
	}

	/**
	 * Sends everything held back since {@link #holdSending()} at once, and stops holding.
	 */
	void sendHeld() {
		this.channel.eventLoop().execute(() -> {
			this.holding = false;
			for (Runnable write : this.held) {
				write.run();
			}
			this.held.clear();
		});
	}

	@Override
	public void channelRead(final ChannelHandlerContext context, final Object message) {
		this.enqueue(this.inbound, message, () -> context.fireChannelRead(message), context::fireChannelReadComplete);
	}

	@Override
	public void channelReadComplete(final ChannelHandlerContext context) {
		// Fired after each delayed batch instead.
	}

	@Override
	public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise promise) {
		Runnable write = () -> this.enqueue(this.outbound, message, () -> context.write(message, promise), context::flush);
		if (this.holding) {
			this.held.add(write);
		} else {
			write.run();
		}
	}

	@Override
	public void flush(final ChannelHandlerContext context) {
		// Flushed after each delayed batch instead.
	}

	@Override
	public void channelInactive(final ChannelHandlerContext context) throws Exception {
		for (Delayed delayed : this.inbound) {
			ReferenceCountUtil.release(delayed.message());
		}
		this.inbound.clear();
		super.channelInactive(context);
	}

	private void enqueue(final ArrayDeque<Delayed> queue, final Object message, final Runnable action,
			final Runnable afterBatch) {
		long release = System.nanoTime() + this.delayNanos;
		Delayed last = queue.peekLast();
		if (last != null) {
			release = Math.max(release, last.release());
		}
		queue.add(new Delayed(release, message, action));
		if (queue.size() == 1) {
			this.scheduleDrain(queue, afterBatch);
		}
	}

	private void scheduleDrain(final ArrayDeque<Delayed> queue, final Runnable afterBatch) {
		long wait = Math.max(0L, queue.getFirst().release() - System.nanoTime());
		this.channel.eventLoop().schedule(() -> this.drain(queue, afterBatch), wait, TimeUnit.NANOSECONDS);
	}

	private void drain(final ArrayDeque<Delayed> queue, final Runnable afterBatch) {
		long now = System.nanoTime();
		boolean released = false;
		while (!queue.isEmpty() && queue.getFirst().release() <= now) {
			queue.removeFirst().action().run();
			released = true;
		}
		if (released) {
			afterBatch.run();
		}
		if (!queue.isEmpty()) {
			this.scheduleDrain(queue, afterBatch);
		}
	}

	private record Delayed(long release, Object message, Runnable action) {
	}
}

/**
 *
 */
package org.janelia.saalfeldlab.control.mcu;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

import org.janelia.saalfeldlab.control.IntControl;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
abstract public class MCUControl implements IntControl {

	protected int value = 0;

	protected HashSet<IntConsumer> listeners = new HashSet<>();

	@Override
	public int getValue() {

		return value;
	}

	@Override
	public void setValue(final int value) {

		this.value = value;

		for (final IntConsumer listener : listeners) {
			listener.accept(value);
		}
	}

	@Override
	public Set<IntConsumer> getListeners() {

		return listeners;
	}

	@Override
	public boolean addListener(final IntConsumer listener) {

		return listeners.add(listener);
	}

	@Override
	public boolean removeListener(final IntConsumer listener) {

		return listeners.remove(listener);
	}

	@Override
	public void clearListeners() {

		listeners.clear();
	}

	abstract void update(final int data);
}

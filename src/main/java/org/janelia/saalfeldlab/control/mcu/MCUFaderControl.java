/**
 *
 */
package org.janelia.saalfeldlab.control.mcu;

import java.util.function.IntConsumer;

import org.janelia.saalfeldlab.control.ClippingIntControl;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class MCUFaderControl extends MCUControl implements ClippingIntControl {

	@Override
	public void setValue(final int value) {

		this.value = Math.min(127, Math.max(0, value));

		for (final IntConsumer listener : listeners) {
			listener.accept(value);
		}
	}

	@Override
	public int getMin() {

		return 0;
	}

	@Override
	public int getMax() {

		return 127;
	}

	@Override
	void update(final int data) {

		setValue((0x40 & data) == 0 ? data + 0x40 : data - 0x40);
		System.out.println("fader " + value);
	}
}

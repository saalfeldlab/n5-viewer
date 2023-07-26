/**
 *
 */
package org.janelia.saalfeldlab.control.mcu;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import org.janelia.saalfeldlab.control.VPotControl;

/**
 * V-Pot control using the MCU protocol over MIDI. MCU V-Pots do not do
 * anything meaningful when their value is changed from outside, so the
 * instance does not know about the id of the actual control element but
 * it generates visual feedback via an assigned LED display. Only 11
 * LEDs are used because MCU does not permit more.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class MCUVPotControl extends MCUControl implements VPotControl {

	private static final int STATUS = 0xb0;

	private static final int[] LED_CODES = {0x00, 0x00, 0x10, 0x20, 0x30};

	private int min = 0, max = 127;

	private final int led;
	private int ledType = DISPLAY_FAN;

	private boolean absolute = true;

	private final Receiver rec;

	private final ShortMessage ledMsg = new ShortMessage();

	/* for delayed relative reset of led ring display */
	private final ScheduledThreadPoolExecutor resetExec = new ScheduledThreadPoolExecutor(1);
	private Future<?> resetTask;

	/**
	 *
	 * @param led
	 *            LED display MIDI id associated with this V-Pot
	 * @param rec
	 *            MIDI receiver for LED display
	 */
	public MCUVPotControl(final int led, final Receiver rec) {

		this.led = led;
		this.rec = rec;
	}

	private void send(final ShortMessage msg) throws InvalidMidiDataException {

		rec.send(msg, System.currentTimeMillis());
	}

	public void display() {

		final int ledCode = LED_CODES[ledType];
		final int j;
		if (absolute) {
			final double n = max - min;
			if (listeners.size() == 0) {
				j = 0;
			} else
				switch (ledType) {
				case DISPLAY_NONE:
					j = 0;
					break;
				case DISPLAY_SPREAD:
					j = Math.max(1, Math.min(6, (int)Math.floor(6.0 * (value - min) / n) + 1));
					break;
				default:
					j = Math.max(1, Math.min(0xb, (int)Math.floor((double)0xb * (value - min) / n) + 1));
				}
			try {
				ledMsg.setMessage(STATUS, led, ledCode | j);
				send(ledMsg);
			} catch (final InvalidMidiDataException e) {
				e.printStackTrace();
			}
		} else {
			final int k;
			if (listeners.size() == 0) {
				j = 0;
				k = 0;
			} else
				switch (ledType) {
				case DISPLAY_NONE:
					j = 0;
					k = 0;
					break;
				case DISPLAY_SPREAD:
					j = Math.max(1, Math.min(6, (int)Math.floor(6.0 / 14.0 * Math.abs(value) + 3.0) + 1));
					k = 1;
					break;
				default:
					j = Math.max(1, Math.min(0xb, (int)Math.floor((double)0xb * (value + 7) / 14) + 1));
					k = 6;
				}
			try {
				if (resetTask != null)
					resetTask.cancel(false);
				resetExec.purge();

				ledMsg.setMessage(STATUS, led, ledCode | j);
				send(ledMsg);

				resetTask = resetExec.schedule(() -> {
					try {
						ledMsg.setMessage(STATUS, led, ledCode | k);
						send(ledMsg);
						resetTask = null;
					} catch (final InvalidMidiDataException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}, 200, TimeUnit.MILLISECONDS);
			} catch (final InvalidMidiDataException e) {
				e.printStackTrace();
			}
		}
	}

	void setValueSilently(final int value) {

		this.value = Math.min(max, Math.max(min, value));
	}

	@Override
	public void setValue(final int value) {

		setValueSilently(value);;

		display();

		for (final IntConsumer listener : listeners) {
			listener.accept(value);
		}
	}

	@Override
	public int getMin() {

		return absolute ? min : -7;
	}

	@Override
	public int getMax() {

		return absolute ? max : 7;
	}

	@Override
	public void setMin(final int min) {

		this.min = min;
		display();
	}

	@Override
	public void setMax(final int max) {

		this.max = max;
		display();
	}

	@Override
	public void setMinMax(final int min, final int max) {

		this.min = min;
		this.max = max;
		display();
	}

	@Override
	void update(final int data) {

		final int d = (0x40 & data) == 0 ? data : -(0x0f & data);
		setValue(absolute ? value + d : d);
	}

	@Override
	public boolean isAbsolute() {

		return absolute;
	}

	@Override
	public void setAbsolute(final boolean absolute) {

		this.absolute = absolute;
	}

	@Override
	public void setDisplayType(final int display) {

		this.ledType = display;
		display();
	}

	@Override
	public boolean addListener(final IntConsumer listener) {

		final boolean result = super.addListener(listener);
		display();
		return result;
	}

	@Override
	public boolean removeListener(final IntConsumer listener) {

		final boolean result = super.removeListener(listener);
		display();
		return result;
	}

	@Override
	public void clearListeners() {

		super.clearListeners();
		display();
	}
}

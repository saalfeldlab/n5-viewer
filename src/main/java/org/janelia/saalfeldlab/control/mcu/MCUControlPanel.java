/**
 *
 */
package org.janelia.saalfeldlab.control.mcu;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public abstract class MCUControlPanel implements Receiver {

	private static final int STATUS_CONTROL = 0xb0;
	private static final int STATUS_KEY = 0x90;
	private static final int STATUS_FADER = 0xe8;

	private Transmitter trans = null;
	private Receiver rec = null;

	public MCUControlPanel(final Transmitter trans, final Receiver rec) {

		this.trans = trans;
		this.rec = rec;
		trans.setReceiver(this);
	}

	abstract public MCUVPotControl getVPotControl(final int i);

	abstract protected MCUVPotControl getVPotControlById(final int i);

	abstract public MCUFaderControl getFaderControl(final int i);

	abstract protected MCUFaderControl getFaderControlById(final int i);

	abstract public MCUButtonControl getButtonControl(final int i);

	abstract protected MCUButtonControl getButtonControlById(final int i);

	abstract public int getNumVPotControls();

	abstract public int getNumButtonControls();

	abstract public int getNumFaderControls();



	protected void send(final ShortMessage msg) throws InvalidMidiDataException {

		rec.send(msg, System.currentTimeMillis());
	}

	protected void send(final byte status, final byte data1, final byte data2) throws InvalidMidiDataException {

		send(new ShortMessage(status, data1, data2));
	}

	protected void send(final int status, final int data1, final int data2) throws InvalidMidiDataException {

		send((byte)status, (byte)data1, (byte)data2);
	}

	@Override
	public void send(final MidiMessage msg, final long timeStamp) {

//		System.out.println(timeStamp);
		final byte[] bytes = msg.getMessage();
//		System.out.println("received : " + String.format("%02x %02x %02x", bytes[0], bytes[1], bytes[2]));

		if (msg instanceof ShortMessage) {

			final ShortMessage sm = (ShortMessage)msg;
//			System.out.println(
//					"CMD " + String.format("%02x", sm.getCommand()) +
//					"  CH " + String.format("%02x", sm.getChannel()) +
//					"  DAT1 " + String.format("%02x", sm.getData1()) +
//					"  DAT2 " + String.format("%02x", sm.getData2()));

			final int status = sm.getStatus();
//			System.out.println(sm.getData2());
			switch (status) {
			case STATUS_CONTROL: {
					final int data = sm.getData2();
					final MCUVPotControl control = getVPotControlById(sm.getData1());
					control.update(data);
				}
				break;
			case STATUS_KEY: {
					final int data = sm.getData2();
					final MCUButtonControl key = getButtonControlById(sm.getData1());
					key.update(data);
				}
				break;
			case STATUS_FADER: {
					final int data = sm.getData2();
					final MCUFaderControl fader = getFaderControlById(sm.getData1());
					fader.update(data);
				}
				break;
			}
		}
	}

	@Override
	public void close() {

		trans.close();
		rec.close();
	}
}

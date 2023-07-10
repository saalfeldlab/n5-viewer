/**
 *
 */
package org.janelia.saalfeldlab.control.mcu;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TIntIntHashMap;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class XTouchMiniMCUControlPanel extends MCUControlPanel {

	private static final String DEFAULT_DEVICE_DESCRIPTION = "X-TOUCH MINI";

	private final int[] vpotIds = {0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17};
	private final int[] vpotLedIds = {0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37};
	private final MCUVPotControl[] vpots = new MCUVPotControl[8];
	private final TIntIntHashMap vpotIndexMap  = new TIntIntHashMap(
			vpotIds.length,
			Constants.DEFAULT_LOAD_FACTOR,
			-1,
			-1);
	{
		for (int i = 0; i < vpotIds.length; ++i)
			vpotIndexMap.put(vpotIds[i], i);
	}

	private final int[] keyIds = {
			0x59, 0x5a, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d,
			0x57, 0x58, 0x5b, 0x5c, 0x56, 0x5d, 0x5e, 0x5f,
			0x54, 0x55,
			0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27};

	private final int[] keyLedIds = {
			0x59, 0x5a, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d,
			0x57, 0x58, 0x5b, 0x5c, 0x56, 0x5d, 0x5e, 0x5f,
			0x54, 0x55,
			-1, -1, -1, -1, -1, -1, -1, -1};
	private final MCUButtonControl[] keys = new MCUButtonControl[26];
	private final TIntIntHashMap keyIndexMap  = new TIntIntHashMap(
			keyIds.length,
			Constants.DEFAULT_LOAD_FACTOR,
			-1,
			-1);
	{
		for (int i = 0; i < keyIds.length; ++i)
			keyIndexMap.put(keyIds[i], i);
	}

	private final MCUFaderControl fader = new MCUFaderControl();

	public XTouchMiniMCUControlPanel(final Transmitter trans, final Receiver rec) {

		super(trans, rec);

		for (int i = 0; i < vpots.length; ++i)
			vpots[i] = new MCUVPotControl(vpotLedIds[i], rec);

		for (int i = 0; i < keys.length; ++i)
			keys[i] = new MCUButtonControl(keyLedIds[i], rec);
	}

	@Override
	public MCUVPotControl getVPotControl(final int i) {

		return vpots[i];
	}

	@Override
	protected MCUVPotControl getVPotControlById(final int id) {

		return vpots[vpotIndexMap.get(id)];
	}

	@Override
	public MCUButtonControl getButtonControl(final int i) {

		return keys[i];
	}

	@Override
	protected MCUButtonControl getButtonControlById(final int id) {

		return keys[keyIndexMap.get(id)];
	}

	@Override
	public MCUFaderControl getFaderControl(final int i) {

		return fader;
	}

	@Override
	protected MCUFaderControl getFaderControlById(final int id) {

		return fader;
	}

	@Override
	public int getNumVPotControls() {

		return vpotIds.length;
	}

	@Override
	public int getNumButtonControls() {

		return vpotIds.length;
	}

	@Override
	public int getNumFaderControls() {

		return vpotIds.length;
	}

	/**
	 * Reset all controls.
	 *
	 * @throws InterruptedException if interrupted
	 * @throws InvalidMidiDataException if midi data are invalid
	 */
	public void reset() throws InterruptedException, InvalidMidiDataException {

		final ShortMessage msg = new ShortMessage(ShortMessage.SYSTEM_RESET);
		send(msg);

		/* MC mode */
		msg.setMessage(ShortMessage.CONTROL_CHANGE, 127, 1);
		send(ShortMessage.CONTROL_CHANGE, 127, 1);

		for (final MCUVPotControl vpot : vpots)
			vpot.display();

		for (final MCUButtonControl key : keys)
			key.display();
	}

	public static XTouchMiniMCUControlPanel build(final String deviceDescription) throws InvalidMidiDataException, MidiUnavailableException, InterruptedException {

		MidiDevice transDev = null;
		Transmitter trans = null;
		MidiDevice recDev = null;
		Receiver rec = null;

		for (final Info info : MidiSystem.getMidiDeviceInfo()) {
			final MidiDevice device = MidiSystem.getMidiDevice(info);
			System.out.println(info.getName() + " : " + info.getDescription());
			final String lowerDeviceDescription = deviceDescription.toLowerCase();
			if (info.getDescription().toLowerCase().contains(lowerDeviceDescription) || info.getName().toLowerCase().contains(lowerDeviceDescription)) {
				if (device.getMaxTransmitters() != 0) {
					transDev = device;
					trans = device.getTransmitter();
				}
				if (device.getMaxReceivers() != 0) {
					recDev = device;
					rec = device.getReceiver();
				}
			}
		}

		if (!(trans == null || rec == null)) {
			transDev.open();
			recDev.open();
			final XTouchMiniMCUControlPanel panel = new XTouchMiniMCUControlPanel(trans, rec);
			trans.setReceiver(panel);
			panel.reset();
			return panel;
		} else {
			if (transDev != null)
				transDev.close();
			if (recDev != null)
				recDev.close();
			throw new MidiUnavailableException("No X TOUCH mini MIDI controller found.");
		}
	}

	public static XTouchMiniMCUControlPanel build() throws InvalidMidiDataException, MidiUnavailableException, InterruptedException {

		return build(DEFAULT_DEVICE_DESCRIPTION);
	}

	public static void main(final String... args) throws InvalidMidiDataException, MidiUnavailableException, InterruptedException {

		build();
	}
}

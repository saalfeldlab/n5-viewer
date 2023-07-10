/**
 *
 */
package org.janelia.saalfeldlab.control;

/**
 * A virtual potentiometer control (V-Pot).  V-Pots are report either an
 * absolute position or a relative step size up or down.  Regardless of the
 * reported value, they have no physical start- or end-position, i.e. they are
 * like mouse-wheels and can rotate indefinitely in one direction.
 *
 * V-Pots have a supporting LED display that supports various visual feedback
 * about the current state of the control.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface VPotControl extends AdjustableClippingIntControl {

	/**
	 * no display
	 */
	public static final int DISPLAY_NONE = 0;

	/**
	 * single LED display starting with the first LED and ending with the last
	 * (good for visualizing the position in an absolute range [0,n])
	 */
	public static final int DISPLAY_PAN = 1;

	/**
	 * fan out left or right from the center LED (good for visualizing relative
	 * steps or for a position in an absolute range [-n,n])
	 */
	public static final int DISPLAY_TRIM = 2;

	/**
	 * fan out starting with first LED until all LEDs are on (good for
	 * visualizing the position in an absolute range [0,n])
	 */
	public static final int DISPLAY_FAN = 3;

	/**
	 * fan out left and right from the center LED until all LEDs are on (good
	 * for visualizing the position in an absolute range [0,n])
	 */
	public static final int DISPLAY_SPREAD = 4;

	/*
	 * Returns whether this VPot control tracks an absolute value
	 * or reports relative changes.
	 *
	 * @return true if the control tracks an absolute value
	 *         false if this control reports relative changes
	 */
	public boolean isAbsolute();
	public void setAbsolute(final boolean absolute);

	/**
	 * Set the LED display type to one of the above types.
	 *
	 * TODO figure out if this is the final set which would call for turning
	 *   this into an enum or if we will have to permit other display modes
	 *   (for which keeping this na integer sounds fair).
	 *
	 * @param display the display type
	 */
	public void setDisplayType(final int display);
}

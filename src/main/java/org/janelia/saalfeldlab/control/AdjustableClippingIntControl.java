/**
 *
 */
package org.janelia.saalfeldlab.control;

/**
 * A control element that modifies an integer value but clips
 * it to a given range.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface AdjustableClippingIntControl extends ClippingIntControl {

	public void setMin(final int min);

	public void setMax(final int max);

	public void setMinMax(final int min, final int max);
}

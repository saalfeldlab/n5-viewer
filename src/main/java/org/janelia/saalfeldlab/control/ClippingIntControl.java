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
public interface ClippingIntControl extends IntControl {

	public int getMin();
	public int getMax();
}

/**
 *
 */
package org.janelia.saalfeldlab.control;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface ButtonControl extends ClippingIntControl {

	/*
	 * Returns whether this Key control is a switch or a button.
	 *
	 * @return true if the control is a switch
	 * false if the control is a button
	 */
	public boolean isToggle();

	public void setToggle(final boolean absolute);
}

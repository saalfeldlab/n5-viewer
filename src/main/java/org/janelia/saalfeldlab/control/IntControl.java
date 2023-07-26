/**
 *
 */
package org.janelia.saalfeldlab.control;

import java.util.Set;
import java.util.function.IntConsumer;

/**
 * A control element that modifies an integer value.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface IntControl extends IntConsumer {

	public Set<IntConsumer> getListeners();

	public boolean addListener(IntConsumer listener);

	public boolean removeListener(IntConsumer listener);

	public void clearListeners();

	public int getValue();

	public void setValue(final int i);

	@Override
	public default void accept(final int i) {

		setValue(i);
	}
}

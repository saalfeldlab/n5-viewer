/**
 *
 */
package org.janelia.saalfeldlab.control;

import java.util.Set;
import java.util.function.Consumer;

/**
 * A control element with listeners.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface Control<C> {

	public Set<Consumer<C>> getListeners();

	public boolean addListener(Consumer<C> listener);

	public boolean removeListener(Consumer<C> listener);

	public void clearListeners();

	public C getValue();

	public void setValue(final C c);
}

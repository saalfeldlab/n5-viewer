package org.janelia.saalfeldlab.n5.bdv;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

public abstract class CustomBehaviourController
{
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	public CustomBehaviourController( final InputTriggerConfig config )
	{
		inputAdder = config.inputTriggerAdder( inputTriggerMap, getName() );
		createBehaviour().register();
	}

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	public abstract String getName();
	public abstract String getTriggers();
	public abstract CustomBehaviour createBehaviour();

	protected abstract class CustomBehaviour implements Behaviour
	{
		private final String name;
		private final String[] defaultTriggers;

		private CustomBehaviour( final String name, final String... defaultTriggers )
		{
			this.name = name;
			this.defaultTriggers = defaultTriggers;
		}

		public CustomBehaviour()
		{
			this( getName(), getTriggers() );
		}

		public void register()
		{
			behaviourMap.put( name, this );
			inputAdder.put( name, defaultTriggers );
		}
	}
}
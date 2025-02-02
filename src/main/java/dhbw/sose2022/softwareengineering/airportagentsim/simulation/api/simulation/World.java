package dhbw.sose2022.softwareengineering.airportagentsim.simulation.api.simulation;

import java.util.Collection;

import dhbw.sose2022.softwareengineering.airportagentsim.simulation.api.simulation.entity.Entity;
import dhbw.sose2022.softwareengineering.airportagentsim.simulation.simulation.SimulationWorld;

public sealed interface World permits SimulationWorld {
	
	public int getWidth();
	
	public int getHeight();
	
	public Collection<Entity> getEntities();
	
	public void add(Entity e);
	
	public void addAll(Collection<? extends Entity> c);
	
	public void remove(Entity e);
	
}

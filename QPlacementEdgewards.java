package org.fog.test.perfeval;

//QPlacementEdgewards.java
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;

public class QPlacementEdgewards extends ModulePlacement{

	private ModuleMapping moduleMapping;

	List <AppModule> apMdls;
	List <AppEdge> appedges; 
	List <AppLoop> apploops;
	Particle selected_particel;
	
	@Override

	protected void mapModules() {
			Map<String, List<String>> mapping = moduleMapping.getModuleMapping();
			for(String deviceName : mapping.keySet()){
				FogDevice device = getDeviceByName(deviceName);
				for(String moduleName : mapping.get(deviceName)){
					
					AppModule module = getApplication().getModuleByName(moduleName);
					if(module == null)
						continue;
					createModuleInstanceOnDevice(module, device);
					//getModuleInstanceCountMap().get(device.getId()).put(moduleName, mapping.get(deviceName).get(moduleName));
				}
			}
	}

	public QPlacementEdgewards(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, 
			Application application, ModuleMapping moduleMapping){
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setModuleInstanceCountMap(new HashMap<Integer, Map<String, Integer>>());
	
		for(FogDevice device : getFogDevices())
			getModuleInstanceCountMap().put(device.getId(), new HashMap<String, Integer>());
		
		QtoResourceSheduleing();
		
		mapModules();
	}
	
	private void QtoResourceSheduleing() {
		// TODO Auto-generated method stub
		this.apMdls = getApplication().getModules();
		this.appedges = getApplication().getEdges();
		this.apploops = getApplication().getLoops();
		
		int mdL = 3;
		
		for (int j = 0 ; j < apMdls.size(); j++) {
			System.out.println(apMdls.get(j).getName());
			
			for(FogDevice device : fogDevices){
				if ( device.getLevel()==mdL) {
						moduleMapping.addModuleToDevice(apMdls.get(j).getName(), device.getName());

				}
			}
			if (mdL > 0) mdL--; 
			
			if (mdL == 1) mdL--;
		}	
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}
	
	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}
}
//smart_parking_system.java
package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.placement.ModulePlacementOnlyCloud;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

public class smart_parking_system {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	
	static boolean CLOUD = true;
	
	static int numOfParkingAreas = 10;
	static int numOfCamarasPerArea = 5;
	static double CAMARA_TRANSMISSION_TIME = 5;
	
	public static void main(String[] args) {

		Log.printLine("Starting smart_parking_system...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "smart_parking_system"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			
			System.out.println("==Devices==");
			for(FogDevice device : fogDevices) {
				System.out.println(device.getName() +" (" + device.getHost().getTotalMips() + ")");				
			}

			System.out.println("==Sensors==");
			for(Sensor device : sensors) {
				System.out.println(device.getName());				
			}
			System.out.println("==Actuators==");
			for(Actuator device : actuators) {
				System.out.println(device.getName());				
			}
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping

			Controller controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			if(CLOUD){
			
				controller.submitApplication(application, new ModulePlacementOnlyCloud(fogDevices, sensors, actuators, application));

			}else{
				// Multi Q logic				
				controller.submitApplication(application, 0, new QPlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));

			}
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("smart_parking_system finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}

	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25); // creates the fog device Cloud at the apex of the hierarchy with level=0
		cloud.setParentId(-1);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
		
		fogDevices.add(cloud);
		fogDevices.add(proxy);
		
		for(int i=0;i<numOfParkingAreas;i++){
			addGw(i+"", userId, appId, proxy.getId()); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
		}
		
	}

	private static FogDevice addGw(String id, int userId, String appId, int parentId){
		FogDevice park_area = createFogDevice("f-"+id, 5600, 8000, 10000, 10000, 2, 0.0, 107.339, 83.4333);
		fogDevices.add(park_area);
		park_area.setParentId(parentId);
		park_area.setUplinkLatency(4); // latency of connection between gateways and proxy server is 4 ms
		for(int i=0;i<numOfCamarasPerArea;i++){
			String camara_Id = id+"-"+i;
			FogDevice smart_camara = addSmartCamara(camara_Id, userId, appId, park_area.getId()); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
			smart_camara.setUplinkLatency(2); // latency of connection between the smartphone and proxy server is 4 ms
			fogDevices.add(smart_camara);
		}
		
		Actuator display = new Actuator("a-"+id, userId, appId, "DISPLAY");
		actuators.add(display);
		display.setGatewayDeviceId(park_area.getId());
		display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms

		return park_area;
	}
	
	private static FogDevice addSmartCamara(String id, int userId, String appId, int parentId){
		FogDevice smart_camara = createFogDevice("e-"+id, 1000, 1000, 10000, 270, 3, 0, 87.53, 82.44);
		smart_camara.setParentId(parentId);
		Sensor camaraSensor = new Sensor("s-"+id, "RAW_DATA", userId, appId, new DeterministicDistribution(CAMARA_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
		sensors.add(camaraSensor);
		camaraSensor.setGatewayDeviceId(smart_camara.getId());
		camaraSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
		return smart_camara;
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the EEG Tractor Beam game application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)
		
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("CLIENT_DATA_CLEANER", 10); // adding module CLIENT_DATA_CLEANER to the application model
		application.addAppModule("ObjectIdentifier", 10); // adding module ObjectIdentifier to the application model
		application.addAppModule("Storekeeper", 10); // adding module Storekeeper to the application model
		application.addAppModule("Broadcaster", 10); // adding module Broadcaster to the application model
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */

		application.addAppEdge("RAW_DATA", "CLIENT_DATA_CLEANER", 1000, 4000, "RAW_DATA", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("CLIENT_DATA_CLEANER", "ObjectIdentifier", 4000, 2000, "CLEANED_DATA", Tuple.UP, AppEdge.MODULE); // adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
		application.addAppEdge("ObjectIdentifier", "Storekeeper", 1000, 500,  "UPDATE_DB", Tuple.UP, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Concentration Calculator to Connector module carrying tuples of type PLAYER_GAME_STATE
		application.addAppEdge("ObjectIdentifier", "Broadcaster", 1000, 500, "NOTOIFY_CLIENT", Tuple.UP, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
		application.addAppEdge("ObjectIdentifier", "DISPLAY", 0, 4000, "DISPLAY_PARKING_AREA_STATUS", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("CLIENT_DATA_CLEANER", "RAW_DATA", "CLEANED_DATA", new FractionalSelectivity(1.0)); // 0.9 tuples of type _SENSOR are emitted by Client module per incoming tuple of type EEG 
		application.addTupleMapping("ObjectIdentifier", "CLEANED_DATA", "UPDATE_DB", new FractionalSelectivity(1.0)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION 
		application.addTupleMapping("ObjectIdentifier", "CLEANED_DATA", "NOTOIFY_CLIENT", new FractionalSelectivity(1.0)); // 1.0 tuples of type CONCENTRATION are emitted by Concentration Calculator module per incoming tuple of type _SENSOR 
		application.addTupleMapping("ObjectIdentifier", "CLEANED_DATA", "DISPLAY_PARKING_AREA_STATUS", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE
	
		/*
		 * Defining application loops to monitor the latency of. 
		 * Here, we add only one loop for monitoring : EEG(sensor) -> Client -> Concentration Calculator -> Client -> DISPLAY (actuator)
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("RAW_DATA");add("CLIENT_DATA_CLEANER");add("ObjectIdentifier");add("DISPLAY");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);
		
		return application;
	}
}

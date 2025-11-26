import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.StringVector;

public class SumoConnection {
    public static void main(String[] args) throws Exception {
        // Add SUMO/bin to PATH of your system
        System.loadLibrary("libtracijni");

        String cfg = "SumoConfig/demo.sumocfg"; 
        
        String[] cmd = { "sumo-gui", "-c", cfg, "--start" };

        StringVector sv = new StringVector();
        for (String s : cmd) {
            sv.add(s);
        }

        try {
            Simulation.start(sv);
            System.out.println("Connected to SUMO with config: " + cfg);
            StringVector trafficlight_ids = TrafficLight.getIDList();
            System.out.println("Traffic lights in network (count = " + trafficlight_ids.size() + "):");
            
            for (int i = 0; i < trafficlight_ids.size(); i++) {
                String id = trafficlight_ids.get(i);
                String state = TrafficLight.getRedYellowGreenState(id);
                System.out.println("  " + id + " -> " + state);
            }

            for (int step = 0; step < 30; step++) {
                Simulation.step();
                System.out.println("Simulation step: " + step);
            }
        } finally {
            Simulation.close();
            System.out.println("Simulation closed.");
        }
    }
}

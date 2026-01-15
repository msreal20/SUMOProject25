package hellofx;

import backend.TraaSConnection;
import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import model.VehicleState;
import service.NetworkService; 
import service.TraaSNetworkService; 
import service.TraaSTrafficLightService;
import service.TraaSVehicleService;
import service.TrafficLightService;
import service.VehicleService;

import java.util.List;

import org.eclipse.sumo.libtraci.Vehicle;

// NEU: Für den Bereich Vehicle Density
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class Controller {

    @FXML private VBox simulationContainer;
    @FXML private Label maxSpeedLabel;
    @FXML private Label avgSpeedLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private VBox advancedSettingsBox;
    @FXML private Button settingsButton;
    // NEU: Für den Bereich Congestion hotspot
    @FXML private Label hotspotLabel;

    // NEU: Für den Bereich Vehicle density per edge
    @FXML private TableView<StreetDensity> streetDensityTable;
    @FXML private TableColumn<StreetDensity, String> streetColumn;
    @FXML private TableColumn<StreetDensity, Double> densityColumn;

    private Canvas canvas;
    private GraphicsContext gc;

    private TraaSConnection connection;
    private VehicleService vehicleService;
    private TrafficLightService trafficLightService;
    private NetworkService networkService; 

    private AnimationTimer timer;
    private boolean running = false;

    private static final double SCALE = 3.0; 

    @FXML
    public void initialize() {
        canvas = new Canvas(800, 600);
        gc = canvas.getGraphicsContext2D();
        simulationContainer.getChildren().add(canvas);

        String dllPath = "C:/Program Files (x86)/Eclipse/Sumo/bin/libtracijni.dll";
        String cfgPath = "C:/SumoProject/SumoConfig/demo.sumocfg";
        connection = new TraaSConnection(dllPath, cfgPath);

        vehicleService = new TraaSVehicleService(connection);
        trafficLightService = new TraaSTrafficLightService(connection);
        networkService = new TraaSNetworkService(connection);

        setDisconnectedStatus();
        clearMap();

        timer = new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (now - lastUpdate < 50_000_000) return; 
                lastUpdate = now;
                stepAndRender();
            }
        };

        // NEU: Spalten mit Daten verbinden für Vehicle density per edge
         streetColumn.setCellValueFactory(
         cellData -> new SimpleStringProperty(cellData.getValue().getStreet())
         );

         densityColumn.setCellValueFactory(
         cellData -> new SimpleDoubleProperty(cellData.getValue().getDensity()).asObject()
         );

          // Erstmal Beispiel-Daten laden
         updateVehicleDensity();

    }

    @FXML
    private void onStartSimulation() {
        if (running) return;
        try {
            connection.connect();
            running = true;
            timer.start();
            setConnectedStatus();
        } catch (Exception e) {
            e.printStackTrace();
            connectionStatusLabel.setText("Status: Error");
            connectionStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    private void onStopSimulation() {
        if (!running) return;
        running = false;
        timer.stop();
        connection.disconnect();
        setDisconnectedStatus();
    }

    @FXML
    private void onResetSimulation() {
        onStopSimulation();
        clearMap();
    }

    @FXML
    public void toggleAdvancedSettings() {
        boolean visible = advancedSettingsBox.isVisible();
        advancedSettingsBox.setVisible(!visible);
        advancedSettingsBox.setManaged(!visible);
    }

    private void stepAndRender() {
        try {
            connection.step();
            clearMap();
            drawNetwork();
            drawTrafficLights();
            drawVehicles();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearMap() {
        gc.setFill(Color.web("#27ac5d"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawNetwork() {
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(10); 

        List<String> edges = networkService.getEdgeIds();
        for (String edgeId : edges) {
            if (edgeId.startsWith(":")) continue;

            List<double[]> shape = networkService.getEdgeShape(edgeId);
            if (shape.isEmpty()) continue;

            gc.beginPath();
            for (int i = 0; i < shape.size(); i++) {
                double[] p = shape.get(i);
                double x = p[0] * SCALE;
                double y = canvas.getHeight() - (p[1] * SCALE); // Flip Y
                
                if (i == 0) gc.moveTo(x, y);
                else gc.lineTo(x, y);
            }
            gc.stroke();
        }
    }

    private void drawVehicles() {
        List<String> ids = vehicleService.getVehicleIds();
        double maxSpeed = 0;
        double sumSpeed = 0;

        gc.setFill(Color.YELLOW);

        for (String id : ids) {
            double[] pos = vehicleService.getVehiclePos(id);
            VehicleState s = new VehicleState(id);
    
            s.setX(pos[0]);
            s.setY(pos[1]);
            s.setSpeed(vehicleService.getVehicleSpeed(id));

            double x = s.getX() * SCALE;
            double y = canvas.getHeight() - (s.getY() * SCALE);

            gc.fillRect(x - 5, y - 5, 10, 10); 

            double v = s.getSpeed() * 3.6;
            sumSpeed += v;
            if (v > maxSpeed) maxSpeed = v;
        }

        if (!ids.isEmpty()) {
            double avg = sumSpeed / ids.size();
            maxSpeedLabel.setText(String.format("Max: %.1f km/h", maxSpeed));
            avgSpeedLabel.setText(String.format("Average: %.1f km/h", avg));
        }
    }

    private void drawTrafficLights() {
    
        List<String> tlIds = trafficLightService.getTrafficLightIds();
        if (tlIds.isEmpty()) return;

        String id = tlIds.get(0); 
        String state = trafficLightService.getTrafficLightState(id);
        
        Color c = (state.toLowerCase().startsWith("r")) ? Color.RED : Color.LIMEGREEN;

        double x = 100 * SCALE; 
        double y = canvas.getHeight() - (100 * SCALE);

        gc.setFill(c);
        gc.fillOval(x - 10, y - 10, 20, 20);
    }

    private void setConnectedStatus() {
        connectionStatusLabel.setText("Status: Live Sumo Connection");
        connectionStatusLabel.setStyle("-fx-text-fill: green;");
    }

    private void setDisconnectedStatus() {
        connectionStatusLabel.setText("Status: No Live Sumo Connection");
        connectionStatusLabel.setStyle("-fx-text-fill: red;");
    }

    @FXML         // NEU: Bereich Congestion Hotspot
    public void updateHotspot() {
    // Hier kann später Daten einfügen
    
    hotspotLabel.setText("Hotspot: Hauptstraße (max. Dichte)");
    hotspotLabel.setStyle("-fx-text-fill: green;");
    }

    private void updateVehicleDensity() {

    ObservableList<StreetDensity> data = FXCollections.observableArrayList();

    // NEU: Beispiel für Straßen in Vehicle density per edge
    data.add(new StreetDensity("Street A", 12.5));
    data.add(new StreetDensity("Street B", 28.3));
    data.add(new StreetDensity("Street C", 6.7));
    data.add(new StreetDensity("Street D", 19.1));

    streetDensityTable.setItems(data);
}


}

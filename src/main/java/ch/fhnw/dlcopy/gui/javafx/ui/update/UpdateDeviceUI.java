
package ch.fhnw.dlcopy.gui.javafx.ui.update;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.MEGA;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.ProcessExecutor;
import java.io.IOException;
import java.util.HashMap;
import javafx.fxml.FXML;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Button;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 *
 * @author user
 */
public class UpdateDeviceUI  extends View{
    
    private static final Logger LOGGER = Logger.getLogger(UpdateDeviceUI.class.getName());
    private static final ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();
    private SystemSource runningSystemSource;
    
    @FXML private Button btnBack;
    
    public UpdateDeviceUI() {
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        try {

            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        resourcePath = getClass().getResource("/fxml/update/update.fxml");
    }
    
    @Override
    protected void setupEventHandlers() {
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
    }
 }

package dlcopy;

import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.util.NameComponentChooser;

/**
 * tests for dlcopy
 * @author ronny
 */
public class SelectNewSticksTest {

    /**
     * test that new attached sticks are automatically selected
     */
    @Test
    public void testNewSelection() throws Exception {

        List<UsbStorageDevice> debugUsbStorageDevices =
                new ArrayList<UsbStorageDevice>();
        debugUsbStorageDevices.add(new UsbStorageDevice("PNY", "ATTACHE OPTIMA",
                "123", "123", "/dev/sdb", 8000L * DLCopy.MEGA, 512,
                "lernstick", 1234567890));

        String[] arguments = new String[]{
            "--variant", "lernstick",
            "--boot", "/dev/sdb1",
            "--systemsize", "2500000000"
        };
        DLCopy dlCopy = new DLCopy(arguments);
        dlCopy.setVisible(true);
        JFrameOperator frameOperator = new JFrameOperator();
        JButtonOperator usb2usbButtonOperator = new JButtonOperator(
                frameOperator, new NameComponentChooser("usb2usbButton"));
        JButtonOperator nextButtonOperator = new JButtonOperator(frameOperator,
                new NameComponentChooser("nextButton"));
        JListOperator usbStorageDeviceListOperator =
                new JListOperator(frameOperator,
                new NameComponentChooser("storageDeviceList"));
        usb2usbButtonOperator.doClick();

        // test that nextButton is initially enabled
        assertTrue(nextButtonOperator.isEnabled());
        nextButtonOperator.doClick();

        // insert first stick
        dlCopy.setDebugUsbStorageDevices(debugUsbStorageDevices);
        Thread.sleep(2000);
        int[] selectedIndices =
                usbStorageDeviceListOperator.getSelectedIndices();
        assertTrue((selectedIndices.length == 1) && (selectedIndices[0] == 0));

        // insert second stick
        debugUsbStorageDevices.add(new UsbStorageDevice("Corsair",
                "Voyager Mini", "234", "567", "/dev/sdc", 16000L * DLCopy.MEGA,
                512, "lernstick", 1234567890));
        dlCopy.setDebugUsbStorageDevices(debugUsbStorageDevices);
        Thread.sleep(2000);
        selectedIndices = usbStorageDeviceListOperator.getSelectedIndices();
        assertTrue("second stick was not automatically selected",
                (selectedIndices.length == 2) && (selectedIndices[0] == 0)
                && (selectedIndices[1] == 1));
    }
}

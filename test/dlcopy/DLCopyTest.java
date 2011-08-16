package dlcopy;

import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.netbeans.jemmy.util.NameComponentChooser;

/**
 * tests for dlcopy
 * @author ronny
 */
public class DLCopyTest {

    /**
     * Test enabling/disabling and focus of some buttons
     * @throws Exception if any exception occurs
     */
    @Test
    public void testNextButton() throws Exception {

        List<UsbStorageDevice> debugUsbStorageDevices =
                new ArrayList<UsbStorageDevice>();
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "vendor 0",
                "model 0", "rev 0", "device 0", 20L * DLCopy.MEGA, 512));
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "vendor 1",
                "model 1", "rev 0", "device 1", 200L * DLCopy.MEGA, 512));
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "vendor 2",
                "model 2", "rev 0", "device 2", 2000L * DLCopy.MEGA, 512));
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "vendor 3",
                "model 3", "rev 0", "device 3", 2800L * DLCopy.MEGA, 512));
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "vendor 4",
                "model 4", "rev 0", "device 4", 2900L * DLCopy.MEGA, 512));
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "vendor 5",
                "model 5", "rev 0", "device 5", 3100L * DLCopy.MEGA, 512));
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "vendor 6",
                "model 6", "rev 0", "device 6", 8000L * DLCopy.MEGA, 512));

        String[] arguments = new String[]{
            "--variant", "lernstick",
            "--boot", "/dev/sdb1",
            "--systemsize", "2500000000"
        };
        DLCopy dlCopy = new DLCopy(arguments);
        dlCopy.setVisible(true);
        JFrameOperator frameOperator = new JFrameOperator();
        JButtonOperator usb2usbButtonOperator = new JButtonOperator(frameOperator,
                new NameComponentChooser("usb2usbButton"));
        JButtonOperator nextButtonOperator = new JButtonOperator(frameOperator,
                new NameComponentChooser("nextButton"));
        JButtonOperator previousButtonOperator = new JButtonOperator(
                frameOperator, new NameComponentChooser("previousButton"));

        usb2usbButtonOperator.doClick();

        // test that nextButton is initially enabled
        assertTrue(nextButtonOperator.isEnabled());
        assertTrue(nextButtonOperator.hasFocus());
        assertTrue(nextButtonOperator.isDefaultButton());
        nextButtonOperator.doClick();

        Thread.sleep(1000);
        assertTrue(previousButtonOperator.hasFocus());
        assertTrue("previousButton is not the default button",
                previousButtonOperator.isDefaultButton());

        previousButtonOperator.doClick();
        Thread.sleep(1000);
        assertTrue(nextButtonOperator.isEnabled());
        assertTrue(nextButtonOperator.hasFocus());
        assertTrue(nextButtonOperator.isDefaultButton());
        nextButtonOperator.doClick();

        dlCopy.setDebugUsbStorageDevices(debugUsbStorageDevices);
        Thread.sleep(2000);

        // test that values in exchangePartitionSizeTextField are not lost
        // because of list updates
        JListOperator usbStorageDeviceListOperator =
                new JListOperator(frameOperator,
                new NameComponentChooser("storageDeviceList"));
        usbStorageDeviceListOperator.setSelectedIndex(3);
        JTextFieldOperator exchangePartitionSizeTextFieldOperator =
                new JTextFieldOperator(frameOperator,
                new NameComponentChooser("exchangePartitionSizeTextField"));
        final String testSizeText = "12345";
        exchangePartitionSizeTextFieldOperator.setText(testSizeText);
        Thread.sleep(2000);
        String actualText = exchangePartitionSizeTextFieldOperator.getText();
        assertEquals("list update changed text field",
                testSizeText, actualText);

        // test removing and inserting USB flash drives
        dlCopy.setDebugUsbStorageDevices(null);
        Thread.sleep(2000);
        assertFalse(nextButtonOperator.isEnabled());
        dlCopy.setDebugUsbStorageDevices(debugUsbStorageDevices);
        Thread.sleep(2000);
        assertTrue("nextButton was not correctly enabled",
                nextButtonOperator.isEnabled());
        assertTrue("nextButton was not set as default button",
                nextButtonOperator.isEnabled());
    }
}

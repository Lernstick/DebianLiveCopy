package dlcopy;

import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;
import org.netbeans.jemmy.util.NameComponentChooser;

/**
 * tests for dlcopy
 * @author ronny
 */
public class ExchangePartitionTest {

    /**
     * Test enabling of next button
     */
    @Test
    public void testNextButton() throws Exception {

        List<UsbStorageDevice> debugUsbStorageDevices =
                new ArrayList<UsbStorageDevice>();
        debugUsbStorageDevices.add(new UsbStorageDevice(null, "PNY",
                "ATTACHE OPTIMA", "123", "/dev/sdb", 16000L * DLCopy.MEGA, 
                512));

        String[] arguments = new String[]{
            "--variant", "lernstick",
            "--boot", "/dev/sdb1",
            "--systemsize", "2500000000"
        };
        DLCopy dlCopy = new DLCopy(arguments);
        dlCopy.setDebugUsbStorageDevices(debugUsbStorageDevices);
        dlCopy.setVisible(true);
        JFrameOperator frameOperator = new JFrameOperator();
        JButtonOperator usb2usbButtonOperator = new JButtonOperator(frameOperator,
                new NameComponentChooser("usb2usbButton"));
        usb2usbButtonOperator.doClick();

        // test that nextButton is initially enabled
        JButtonOperator nextButtonOperator = new JButtonOperator(frameOperator,
                new NameComponentChooser("nextButton"));
        assertTrue(nextButtonOperator.isEnabled());
        nextButtonOperator.doClick();

        // test that exchange partition size defaults to zero
        JTextFieldOperator exchangePartitionSizeTextFieldOperator =
                new JTextFieldOperator(frameOperator,
                new NameComponentChooser("exchangePartitionSizeTextField"));
        String sizeText = exchangePartitionSizeTextFieldOperator.getText();
        assertEquals("exchange partition does not default to zero",
                "0", sizeText);
    }
}
package frc.robot;

import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;

public class Constants {

    public static final class VisionConstants {
        public static final Transform3d leftTransform  = new Transform3d(0, 0.2, 0, new Rotation3d(0, -0.5 * Math.PI, 0)); // FIXME Give me values that are accurate please
        public static final Transform3d rightTransform = new Transform3d(0, -0.2, 0, new Rotation3d(0, 0.5 * Math.PI, 0)); // FIXME Give me accurate values as well please.
        public static final String leftCameraName = "Cam2";
        public static final String rightCameraName = "Cam1";
    }

    public static final class ShooterConstants {
        public static final int leftMotorID = 40;
        public static final int rightMotorID = 41;
    }

    public static final class IntakeConstants {
        public static final int intakeMotorID = 0;
        public static final double intakeSpeed = 0.5;
        public static final double ejectSpeed = -0.5;
    }

    public static final class InputConstants {
        public static final int kDriverControllerPort = 0;
        public static final int kOperatorControllerPort = 1;

        public static final int leftTriggerID = 2;
        public static final int rightTriggerID = 3;

        public static final double triggerTolerance = 0.5;
    }
}

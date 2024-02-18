package frc.robot.subsystems;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

import org.photonvision.EstimatedRobotPose;

import com.ctre.phoenix6.Utils;
import com.ctre.phoenix6.configs.CurrentLimitsConfigs;
import com.ctre.phoenix6.mechanisms.swerve.SwerveDrivetrain;
import com.ctre.phoenix6.mechanisms.swerve.SwerveDrivetrainConstants;
import com.ctre.phoenix6.mechanisms.swerve.SwerveModuleConstants;
import com.ctre.phoenix6.mechanisms.swerve.SwerveRequest;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.PIDConstants;
import com.pathplanner.lib.util.ReplanningConfig;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Notifier;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.Subsystem;
import frc.robot.Constants.SwerveConstants;
import frc.robot.generated.TunerConstants;

public class CommandSwerveDrivetrain extends SwerveDrivetrain implements Subsystem {
    private static final double kSimLoopPeriod = 0.002; // 5 ms
    private Notifier m_simNotifier = null;
    private double m_lastSimTime;
    private final SwerveRequest.ApplyChassisSpeeds autoRequest = new SwerveRequest.ApplyChassisSpeeds();

    private Pose2d estimatedPose;

    private HashMap<String, Command> eventMarkers = new HashMap<>();

    private PhotonVision photonVision;
    private boolean isInRange;

    private void initPhotonVision() {
        photonVision = new PhotonVision();
    }

    public CommandSwerveDrivetrain(SwerveDrivetrainConstants driveTrainConstants, double OdometryUpdateFrequency,
            SwerveModuleConstants... modules) {
        super(driveTrainConstants, OdometryUpdateFrequency, modules);
        configurePathPlanner();
        if (Utils.isSimulation()) {
            startSimThread();
        }
        initPhotonVision();
    }

    public CommandSwerveDrivetrain(SwerveDrivetrainConstants driveTrainConstants, SwerveModuleConstants... modules) {
        super(driveTrainConstants, modules);
        configurePathPlanner();
        if (Utils.isSimulation()) {
            startSimThread();
        }
        initPhotonVision();
    }

    private void configurePathPlanner() {
        double driveBaseRadius = 0;
        for (var moduleLocation : m_moduleLocations) {
            driveBaseRadius = Math.max(driveBaseRadius, moduleLocation.getNorm());
        }

        eventMarkers.put("Shoot", new InstantCommand());
        eventMarkers.put("Intake", new InstantCommand());
        NamedCommands.registerCommands(eventMarkers);

        AutoBuilder.configureHolonomic(
                () -> this.getState().Pose, // CurrentPose Supplier
                this::seedFieldRelative, // PoseSetter Consumer
                this::getCurrentRobotChassisSpeeds,
                (speeds) -> this.setControl(autoRequest.withSpeeds(speeds)), // ChassisSpeeds Consumer
                new HolonomicPathFollowerConfig(
                        new PIDConstants(SwerveConstants.kPDrive, SwerveConstants.kIDrive, SwerveConstants.kDDrive),
                        new PIDConstants(SwerveConstants.kPSteer, SwerveConstants.kISteer, SwerveConstants.kDSteer),
                        TunerConstants.kSpeedAt12VoltsMps,
                        driveBaseRadius,
                        new ReplanningConfig(true, true)),
                () -> {
                    var alliance = DriverStation.getAlliance();
                    return alliance.isPresent() ? alliance.get() == DriverStation.Alliance.Red : false;
                },
                this); // Subsystem for requirements
    }

    public Command applyRequest(Supplier<SwerveRequest> requestSupplier) {
        return run(() -> this.setControl(requestSupplier.get()));
    }

    public ChassisSpeeds getCurrentRobotChassisSpeeds() {
        return m_kinematics.toChassisSpeeds(getState().ModuleStates);
    }

    public Command makeTestAuton() {
        return AutoBuilder.buildAuto("Test Auton");
    }

    private void startSimThread() {
        m_lastSimTime = Utils.getCurrentTimeSeconds();

        /* Run simulation at a faster rate so PID gains behave more reasonably */
        m_simNotifier = new Notifier(() -> {
            final double currentTime = Utils.getCurrentTimeSeconds();
            double deltaTime = currentTime - m_lastSimTime;
            m_lastSimTime = currentTime;

            /* use the measured time delta, get battery voltage from WPILib */
            updateSimState(deltaTime, RobotController.getBatteryVoltage());
        });
        m_simNotifier.startPeriodic(kSimLoopPeriod);
    }

    private void updateOdometry() {
        Optional<EstimatedRobotPose> leftPoseMaybe = photonVision.getGlobalPoseFromLeft();
        Optional<EstimatedRobotPose> rightPoseMaybe = photonVision.getGlobalPoseFromRight();

        SmartDashboard.putBoolean("SeesRight", rightPoseMaybe.isPresent());
        SmartDashboard.putBoolean("SeesLeft", leftPoseMaybe.isPresent());

        if (leftPoseMaybe.isPresent()) {
            EstimatedRobotPose leftPose = leftPoseMaybe.get();
            SmartDashboard.putString("Left", leftPose.estimatedPose.toString());
            addVisionMeasurement(leftPose.estimatedPose.toPose2d(), leftPose.timestampSeconds);
        }
        if (rightPoseMaybe.isPresent()) {
            EstimatedRobotPose rightPose = rightPoseMaybe.get();
            SmartDashboard.putString("Right", rightPose.estimatedPose.toString());
            addVisionMeasurement(rightPose.estimatedPose.toPose2d(), rightPose.timestampSeconds);
        }
        estimatedPose = m_odometry.getEstimatedPosition();
        SmartDashboard.putString("ROBOTPOSE", estimatedPose.toString());
        SmartDashboard.putNumber("ROBOTX", estimatedPose.getX());
        SmartDashboard.putNumber("ROBOTY", estimatedPose.getY());
    }

    public Pose2d getPose() {
        return estimatedPose;
    }

    public void setCurrentLimit(double limit) {
        CurrentLimitsConfigs configs = new CurrentLimitsConfigs()
                .withSupplyCurrentLimitEnable(true)
                .withSupplyCurrentLimit(limit);

        for (int i = 0; i < 3; i++)
            getModule(i).getDriveMotor().getConfigurator().apply(configs);
        getModule(3).getDriveMotor().getConfigurator().apply(configs, 0.015);
    }

    @Override
    public void periodic() {
        updateOdometry();
    }

    public boolean isInRange() {
        return isInRange;
    }

    public void setInRange(boolean isInRange) {
        this.isInRange = isInRange;
    }
}

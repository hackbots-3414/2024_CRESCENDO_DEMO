// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix6.Utils;
import com.ctre.phoenix6.signals.NeutralModeValue;
import com.pathplanner.lib.auto.AutoBuilder;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import frc.robot.commands.ElevatorCommand.ElevatorPresets;
import frc.robot.subsystems.NoteFinder;
import frc.robot.subsystems.SubsystemManager;

public class RobotContainer {
  public enum RepathChoices {SHOOTER,AMP,SOURCE,NULL;}
  
  private final Joystick driver = new Joystick(Constants.InputConstants.kDriverControllerPort);
  private final JoystickButton resetGyroButton = new JoystickButton(driver, Constants.DriverConstants.resetGyroButton);
  
  private final CommandXboxController operator = new CommandXboxController(Constants.InputConstants.kOperatorControllerPort);

  SendableChooser<Command> pathChooser = new SendableChooser<>();

  public SubsystemManager subsystemManager = new SubsystemManager();
  
  private void configureDriverBindings() {
    resetGyroButton.onTrue(subsystemManager.makeResetCommand());
    subsystemManager.configureDriveDefaults(() -> -driver.getRawAxis(Constants.DriverConstants.leftY), () -> driver.getRawAxis(Constants.DriverConstants.leftX), () -> driver.getRawAxis(Constants.DriverConstants.rightX));

    // joystick.a().whileTrue(subsystemManager.makeBrakeCommand());
    // joystick.b().whileTrue(subsystemManager.makePointCommand(joystick.getLeftX(), joystick.getLeftY()));
    // joystick.leftBumper().onTrue(subsystemManager.makeResetCommand());

    if (Utils.isSimulation()) {subsystemManager.resetAtPose2d(new Pose2d(new Translation2d(), Rotation2d.fromDegrees(90)));}
    subsystemManager.telemeterize();
  }

  private void configureOperatorBindings() {
    operator.b().whileTrue(subsystemManager.makeShootCommand()); // shoot manually
    operator.x().whileTrue(subsystemManager.makeIntakeCommand()); // intake
    operator.a().whileTrue(subsystemManager.makeAmpScoreCommand()); // auto amp (will do everything)
    operator.y().whileTrue(subsystemManager.makeTrapScoreCommand()); // auto trap (will do everything)

    operator.leftBumper().whileTrue(subsystemManager.makeElevatorCommand(ElevatorPresets.TRAP));
    operator.rightBumper().whileTrue(subsystemManager.makeEjectCommand());

    operator.back().whileTrue(subsystemManager.makeWinchCommand(true));
    operator.start().whileTrue(subsystemManager.makeWinchCommand(false));

    // operator.axisGreaterThan(Constants.InputConstants.leftTriggerID, Constants.InputConstants.triggerTolerance).whileTrue(<ADD COMMAND>); // Left Trigger as Button
    // operator.axisGreaterThan(Constants.InputConstants.rightTriggerID, Constants.InputConstants.triggerTolerance).whileTrue(<ADD COMMAND>); //Right Trigger as Button

    operator.pov(0).whileTrue(subsystemManager.makeManualElevatorCommand(true));// D-PAD Up
    operator.pov(180).whileTrue(subsystemManager.makeManualElevatorCommand(false));// D-PAD Down
    operator.pov(90).whileTrue(subsystemManager.makeManualPivotCommand(true));// D-PAD Up
    operator.pov(270).whileTrue(subsystemManager.makeManualPivotCommand(false));// D-PAD Down

    SmartDashboard.putData("Coast Elevator", subsystemManager.elevatorNeutralMode(NeutralModeValue.Coast));
    SmartDashboard.putData("Brake Elevator", subsystemManager.elevatorNeutralMode(NeutralModeValue.Brake));
  }

  public RobotContainer() {
    configureDriverBindings();
    configureOperatorBindings();

    pathChooser = AutoBuilder.buildAutoChooser();
    SmartDashboard.putData("Auto Chooser", pathChooser);
  }

  public Command getAutonomousCommand() {return pathChooser.getSelected();}
  public NoteFinder getNoteFinder() {return subsystemManager.getNoteFinder();}
}

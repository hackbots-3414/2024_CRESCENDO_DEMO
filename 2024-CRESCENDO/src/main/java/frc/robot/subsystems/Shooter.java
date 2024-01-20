package frc.robot.subsystems;

import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.Follower;
import com.ctre.phoenix6.hardware.TalonFX;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;

public class Shooter extends SubsystemBase {

  private TalonFX leftMotor;
  private TalonFX rightMotor;

  private DigitalInput irSensor = new DigitalInput(Constants.ShooterConstants.irSensorChannel);

  public Shooter() {
    leftMotor = new TalonFX(Constants.ShooterConstants.leftMotorID);
    rightMotor = new TalonFX(Constants.ShooterConstants.rightMotorID);

    leftMotor.getConfigurator().apply(new TalonFXConfiguration());
    rightMotor.getConfigurator().apply(new TalonFXConfiguration());

    leftMotor.clearStickyFaults();
    rightMotor.clearStickyFaults();

    rightMotor.setInverted(Constants.ShooterConstants.shooterMotorInvert);
    
    leftMotor.setControl(new Follower(rightMotor.getDeviceID(), true));
  }

  public void setMotor(double speed) {
    rightMotor.set(speed);
  }

  public void stopMotor() {
    rightMotor.set(0);
  }

  public boolean getIR() {
    return irSensor.get();
  }

  @Override
  public void periodic() {
    SmartDashboard.putBoolean("IR Sensor", getIR());
  }
}

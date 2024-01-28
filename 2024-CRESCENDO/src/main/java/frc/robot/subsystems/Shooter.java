package frc.robot.subsystems;

import static edu.wpi.first.units.MutableMeasure.mutable;
import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.Volts;

import com.ctre.phoenix6.configs.CurrentLimitsConfigs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.Follower;
import com.ctre.phoenix6.hardware.TalonFX;

import edu.wpi.first.units.Angle;
import edu.wpi.first.units.Measure;
import edu.wpi.first.units.MutableMeasure;
import edu.wpi.first.units.Velocity;
import edu.wpi.first.units.Voltage;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.Constants;

public class Shooter extends SubsystemBase implements AutoCloseable {

  private TalonFX leftMotor;
  private TalonFX rightMotor;
  
  private boolean isRunning = false;

 // Mutable holder for unit-safe voltage values, persisted to avoid reallocation.
 private final MutableMeasure<Voltage> m_appliedVoltage = mutable(Volts.of(0));
 // Mutable holder for unit-safe linear distance values, persisted to avoid reallocation.
 private final MutableMeasure<Angle> m_distance = mutable(Radians.of(0));
 // Mutable holder for unit-safe linear velocity values, persisted to avoid reallocation.
 private final MutableMeasure<Velocity<Angle>> m_velocity = mutable(RadiansPerSecond.of(0));

private final SysIdRoutine m_sysIdRoutine =
    new SysIdRoutine(
        // Empty config defaults to 1 volt/second ramp rate and 7 volt step voltage.
        new SysIdRoutine.Config(),
        new SysIdRoutine.Mechanism(
            // Tell SysId how t o plumb the driving voltage to the motors.
            (Measure<Voltage> volts) -> {
              leftMotor.setVoltage(volts.in(Volts));
              rightMotor.setVoltage(volts.in(Volts));
            },
            // Tell SysId how to record a frame of data for each motor on the mechanism being
            // characterized.
            log -> {
              // Record a frame for the left motors.  Since these share an encoder, we consider
              // the entire group to be one motor.
              log.motor("Shooter Flywheel")
                  .voltage(
                      m_appliedVoltage.mut_replace(
                          getMotorSpeed() * RobotController.getBatteryVoltage(), Volts))
                  .angularPosition(m_distance.mut_replace(getMotorPosRad(), Radians))
                  .angularVelocity(m_velocity.mut_replace(getMotorVeloRad(), RadiansPerSecond));
            },
            // Tell SysId to make generated commands require this subsystem, suffix test state in
            // WPILog with this subsystem's name ("Shooter")
            this));

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

  public void setMotor(double speed) {rightMotor.set(speed);}
  public void stopMotor() {rightMotor.set(0);}

  @Override
  public void periodic() {}

  public double getMotorPos() {return (leftMotor.getPosition().getValueAsDouble() + rightMotor.getPosition().getValueAsDouble()) / 2.0;}
  public double getMotorPosRad() {return getMotorPos() * 2.0 * Math.PI;}
  public double getMotorVelo() {return (leftMotor.getVelocity().getValueAsDouble() + rightMotor.getVelocity().getValueAsDouble()) / 2.0;}
  public double getMotorSpeed() {return (leftMotor.get() + rightMotor.get()) / 2.0;}
  public double getMotorVeloRad() {return getMotorVelo() * 2.0 * Math.PI;}

  public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {return m_sysIdRoutine.quasistatic(direction);}
  public Command sysIdDynamic(SysIdRoutine.Direction direction) {return m_sysIdRoutine.dynamic(direction);}

  public void setCurrentLimit(double limit) {
    CurrentLimitsConfigs configs = new CurrentLimitsConfigs().withSupplyCurrentLimitEnable(true).withSupplyCurrentLimit(limit);
    leftMotor.getConfigurator().apply(configs, 0.01);
    rightMotor.getConfigurator().apply(configs, 0.01);
  }

  public void setRunning(boolean isRunning) {this.isRunning = isRunning;}
  public boolean getRunning() {return this.isRunning;}
  
  @Override
  public void close() throws Exception {
    leftMotor.close();
    rightMotor.close();
  }
}

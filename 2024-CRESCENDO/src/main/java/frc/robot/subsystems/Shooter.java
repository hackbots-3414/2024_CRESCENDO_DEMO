package frc.robot.subsystems;

import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.DutyCycleOut;
import com.ctre.phoenix6.controls.Follower;
import com.ctre.phoenix6.controls.VelocityDutyCycle;
import com.ctre.phoenix6.controls.compound.Diff_DutyCycleOut_Velocity;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.sim.TalonFXSimState;

import edu.wpi.first.units.MutableMeasure;
import edu.wpi.first.units.Velocity;

import static edu.wpi.first.units.MutableMeasure.mutable;
import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.MetersPerSecond;
import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.Volts;
import edu.wpi.first.units.Voltage;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.units.Angle;
import edu.wpi.first.units.Distance;
import edu.wpi.first.units.Measure;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.Constants;
import frc.robot.Constants.ShooterConstants;

public class Shooter extends SubsystemBase implements AutoCloseable {

  private TalonFX leftMotor;
  private TalonFX rightMotor;

  double motorVelocity = 0.0;

  // Mutable holder for unit-safe voltage values, persisted to avoid reallocation.
  private final MutableMeasure<Voltage> m_appliedVoltage = mutable(Volts.of(0));
  // Mutable holder for unit-safe linear distance values, persisted to avoid
  // reallocation.
  private final MutableMeasure<Angle> m_distance = mutable(Radians.of(0));
  // Mutable holder for unit-safe linear velocity values, persisted to avoid
  // reallocation.
  private final MutableMeasure<Velocity<Angle>> m_velocity = mutable(RadiansPerSecond.of(0));

  private final SysIdRoutine m_sysIdRoutine = new SysIdRoutine(
      // Empty config defaults to 1 volt/second ramp rate and 7 volt step voltage.
      new SysIdRoutine.Config(),
      new SysIdRoutine.Mechanism(
          // Tell SysId how to plumb the driving voltage to the motors.
          (Measure<Voltage> volts) -> {
            leftMotor.setVoltage(volts.in(Volts));
            rightMotor.setVoltage(volts.in(Volts));
          },
          // Tell SysId how to record a frame of data for each motor on the mechanism
          // being
          // characterized.
          log -> {
            // Record a frame for the left motors. Since these share an encoder, we consider
            // the entire group to be one motor.
            log.motor("Shooter Flywheel")
                .voltage(
                    m_appliedVoltage.mut_replace(
                        getMotorSpeed() * RobotController.getBatteryVoltage(), Volts))
                .angularPosition(m_distance.mut_replace(getMotorPosRad(), Radians))
                .angularVelocity(m_velocity.mut_replace(getMotorVeloRad(), RadiansPerSecond));
          },
          // Tell SysId to make generated commands require this subsystem, suffix test
          // state in
          // WPILog with this subsystem's name ("Shooter")
          this));

  private final Slot0Configs PIDConfig = new Slot0Configs()
    .withKP(ShooterConstants.kP)
    .withKI(ShooterConstants.kI)
    .withKD(ShooterConstants.kD);

  public Shooter() {
    leftMotor = new TalonFX(Constants.ShooterConstants.leftMotorID);
    rightMotor = new TalonFX(Constants.ShooterConstants.rightMotorID);

    leftMotor.getConfigurator().apply(new TalonFXConfiguration().withSlot0(PIDConfig));
    rightMotor.getConfigurator().apply(new TalonFXConfiguration().withSlot0(PIDConfig));

    leftMotor.clearStickyFaults();
    rightMotor.clearStickyFaults();

    rightMotor.setInverted(Constants.ShooterConstants.shooterMotorInvert);

    leftMotor.setControl(new Follower(rightMotor.getDeviceID(), true));
  }

  public void stopMotor() {
    rightMotor.setControl(new VelocityDutyCycle(0.0));
  }

  public void setFlywheelVelo(double velocity) {
    rightMotor.setControl(new VelocityDutyCycle(velocity));
  }

  @Override
  public void periodic() {
    motorVelocity = (leftMotor.getVelocity().getValueAsDouble() + rightMotor.getVelocity().getValueAsDouble()) / 2.0;
  
    SmartDashboard.putNumber("Flywheel Velocity", motorVelocity);
  }

  public double getMotorPos() {
    return (leftMotor.getPosition().getValueAsDouble() + rightMotor.getPosition().getValueAsDouble()) / 2.0;
  }

  public double getMotorPosRad() {
    return getMotorPos() * 2.0 * Math.PI;
  }

  public double getMotorVelo() {
    return motorVelocity;
  }

  public double getMotorSpeed() {
    return (leftMotor.get() + rightMotor.get()) / 2.0;
  }

  public double getMotorVeloRad() {
    return getMotorVelo() * 2.0 * Math.PI;
  }

  public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
    return m_sysIdRoutine.quasistatic(direction);
  }

  public Command sysIdDynamic(SysIdRoutine.Direction direction) {
    return m_sysIdRoutine.dynamic(direction);
  }

  @Override
  public void close() throws Exception {
    leftMotor.close();
    rightMotor.close();
  }

  public TalonFXSimState getSimStateLeft() {
    return leftMotor.getSimState();
  }

  public TalonFXSimState getSimStateRight() {
    return rightMotor.getSimState();
  }

  public StatusSignal<Double> getMotorDutyCycle() {
    return rightMotor.getDutyCycle();
  }

  public void setControl(DutyCycleOut dutyCycleOut) {
    rightMotor.setControl(dutyCycleOut);
  }
}

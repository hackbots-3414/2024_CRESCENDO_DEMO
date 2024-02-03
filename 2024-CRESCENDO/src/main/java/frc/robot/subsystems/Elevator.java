package frc.robot.subsystems;

import static edu.wpi.first.units.MutableMeasure.mutable;
import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.Volts;

import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.CurrentLimitsConfigs;
import com.ctre.phoenix6.configs.MotionMagicConfigs;
import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.Follower;
import com.ctre.phoenix6.controls.MotionMagicVoltage;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.AbsoluteSensorRangeValue;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import com.ctre.phoenix6.signals.SensorDirectionValue;
import com.ctre.phoenix6.sim.TalonFXSimState;

import edu.wpi.first.math.controller.ElevatorFeedforward;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.units.Angle;
import edu.wpi.first.units.Measure;
import edu.wpi.first.units.MutableMeasure;
import edu.wpi.first.units.Velocity;
import edu.wpi.first.units.Voltage;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.lib.math.Conversions;
import frc.robot.Constants;
import frc.robot.Constants.ElevatorConstants;
import frc.robot.Constants.ElevatorConstants.ElevatorMotionMagicConstants;
import frc.robot.Constants.ElevatorConstants.ElevatorSlot0ConfigConstants;

public class Elevator extends SubsystemBase implements AutoCloseable {

  private TalonFX elevatorMotor = new TalonFX(ElevatorConstants.elevatorMotorID);

  private DigitalInput m_forwardLimit = new DigitalInput(Constants.ElevatorConstants.forwardLimitChannelID);
  private DigitalInput m_reverseLimit = new DigitalInput(Constants.ElevatorConstants.reverseLimitChannelID);

  private double elevatorPosition;
  
  // Mutable holder for unit-safe voltage values, persisted to avoid reallocation.
  private final MutableMeasure<Voltage> m_appliedVoltage = mutable(Volts.of(0));
  // Mutable holder for unit-safe linear distance values, persisted to avoid reallocation.
  private final MutableMeasure<Angle> m_distance = mutable(Radians.of(0));
  // Mutable holder for unit-safe linear velocity values, persisted to avoid reallocation.
  private final MutableMeasure<Velocity<Angle>> m_velocity = mutable(RadiansPerSecond.of(0));

  private final SysIdRoutine m_sysIdRoutine = new SysIdRoutine(
      // Empty config defaults to 1 volt/second ramp rate and 7 volt step voltage.
      new SysIdRoutine.Config(),
      new SysIdRoutine.Mechanism(
          // Tell SysId how to plumb the driving voltage to the motors.
          (Measure<Voltage> volts) -> {
            elevatorMotor.setVoltage(volts.in(Volts));
          },
          // Tell SysId how to record a frame of data for each motor on the mechanism
          // being
          // characterized.
          log -> {
            // Record a frame for the left motors. Since these share an encoder, we consider
            // the entire group to be one motor.
            log.motor("Elevator Motors")
                .voltage(
                    m_appliedVoltage.mut_replace(
                        getMotorSpeed() * RobotController.getBatteryVoltage(), Volts))
                // .angularPosition(m_distance.mut_replace(getMeasurement(), Radians))
                // .angularVelocity(m_velocity.mut_replace(getCanCoderVelo(), RadiansPerSecond));
                .angularPosition(m_distance.mut_replace(elevatorPosition, Radians))
                .angularVelocity(m_velocity.mut_replace(Units.rotationsPerMinuteToRadiansPerSecond(getMotorVelo()), RadiansPerSecond));
          },

          // Tell SysId to make generated commands require this subsystem, suffix test
          // state in
          // WPILog with this subsystem's name ("Shooter")
          this));
  
  private Slot0Configs slot0Config = new Slot0Configs()
    .withKP(ElevatorSlot0ConfigConstants.kP)
    .withKI(ElevatorSlot0ConfigConstants.kI)
    .withKD(ElevatorSlot0ConfigConstants.kD)
    .withKS(ElevatorSlot0ConfigConstants.kS)
    .withKV(ElevatorSlot0ConfigConstants.kV)
    .withKA(ElevatorSlot0ConfigConstants.kA);

  private MotionMagicConfigs motionMagicConfig = new MotionMagicConfigs()
    .withMotionMagicCruiseVelocity(ElevatorMotionMagicConstants.cruiseVelocity)
    .withMotionMagicAcceleration(ElevatorMotionMagicConstants.acceleration)
    .withMotionMagicJerk(ElevatorMotionMagicConstants.jerk);

  private MotionMagicVoltage m_request = new MotionMagicVoltage(0); // FIXME inital pos might be current pos insted of 0

  private boolean isRunning = false;

  public Elevator() {
    configElevatorMotors();
    Timer.delay(0.1);
  }

  private void configElevatorMotors() {
    elevatorMotor.getConfigurator().apply(new TalonFXConfiguration(), 0.050);

    TalonFXConfiguration configuration = new TalonFXConfiguration()
    .withSlot0(slot0Config)
    .withMotionMagic(motionMagicConfig);

    configuration.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    configuration.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;

    configuration.SoftwareLimitSwitch.ForwardSoftLimitThreshold = Conversions.metersToFalcon(ElevatorConstants.elevatorUpperLimit, ElevatorConstants.circumference, ElevatorConstants.gearRatio); // DO THE MATH
    configuration.SoftwareLimitSwitch.ReverseSoftLimitThreshold = Conversions.metersToFalcon(ElevatorConstants.elevatorUpperLimit, ElevatorConstants.circumference, ElevatorConstants.gearRatio); // DO THE MATH
    configuration.SoftwareLimitSwitch.ForwardSoftLimitEnable = false;
    configuration.SoftwareLimitSwitch.ReverseSoftLimitEnable = false;

    configuration.CurrentLimits.SupplyCurrentLimitEnable = true;
    configuration.CurrentLimits.SupplyCurrentLimit = ElevatorConstants.elevatorCurrentLimit;
    configuration.CurrentLimits.SupplyCurrentThreshold = 0;
    configuration.CurrentLimits.SupplyTimeThreshold = 0;

    elevatorMotor.setSafetyEnabled(true);

    elevatorMotor.getConfigurator().apply(configuration, 0.2);
  }

  public void setElevatorPosition(double position) { // position is in number of rotations as per documentation.
    elevatorMotor.setControl(m_request
      .withPosition(position)
      .withLimitForwardMotion(getForwardLimit())
      .withLimitReverseMotion(getReverseLimit()));
  }

  public void set(double speed) {elevatorMotor.set(speed);} // -1 to 1
  public void stop() {elevatorMotor.set(0.0);}
  public boolean getForwardLimit() {return m_forwardLimit.get();}
  public boolean getReverseLimit() {return m_reverseLimit.get();}

  public double getPosition() {return elevatorPosition;}

  public void setNeutralMode(NeutralModeValue value) {elevatorMotor.setNeutralMode(value);}

  public double getMotorSpeed() {return elevatorMotor.get();}

  public double getMotorPos() {return elevatorMotor.getPosition().getValueAsDouble();}
  
  public double getMotorVelo() {return elevatorMotor.getVelocity().getValueAsDouble();}

   public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
    return m_sysIdRoutine.quasistatic(direction);
  }

  public Command sysIdDynamic(SysIdRoutine.Direction direction) {
    return m_sysIdRoutine.dynamic(direction);
  }

  public void setCurrentLimit(double limit) {
    CurrentLimitsConfigs configs = new CurrentLimitsConfigs().withSupplyCurrentLimitEnable(true).withSupplyCurrentLimit(limit);
    elevatorMotor.getConfigurator().apply(configs, 0.01);
  }

  public void setRunning(boolean isRunning) {this.isRunning = isRunning;}
  public boolean getRunning() {return this.isRunning;}

  @Override
  public void periodic() {
    elevatorPosition = elevatorMotor.getPosition().getValueAsDouble();

    if (getReverseLimit()) {
      elevatorMotor.setPosition(0);
    }
  }

  @Override
  public void close() throws Exception{
    elevatorMotor.close();
  }

  public TalonFXSimState getSimState() {return elevatorMotor.getSimState();}
}
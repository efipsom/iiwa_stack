
#include "iiwa_ros.h"
#include "ros/ros.h"
// iiwa_msgs and ROS inteface includes
#include <iiwa_msgs/JointPosition.h>
#include <iiwa_msgs/ConfigureSmartServo.h>

// Main function
int main(int argc, char **argv)
{
  printf("Starting iiwa_test node\n");
  // Init ROS library
  ros::init(argc, argv, "iiwa_test");
  // Create node handle
  ros::NodeHandle n;

  iiwa_ros::iiwaRos my_iiwa_ros_object;
  iiwa_msgs::JointPosition my_joint_position;

  my_iiwa_ros_object.init();

  bool success = false;
  if (my_iiwa_ros_object.getJointPosition(my_joint_position)) {
      ROS_INFO_STREAM("I got a Joint Message and its contents is: " << my_joint_position.position.a1 << " " << my_joint_position.position.a2 << " " << my_joint_position.position.a3 << " " << my_joint_position.position.a4 << " " << my_joint_position.position.a5 << " " << my_joint_position.position.a6 << " " << my_joint_position.position.a7);
  }
  else {
      ROS_INFO_STREAM("No Joint Message available");
  }

  my_iiwa_ros_object.getPathParametersService().setJointRelativeVelocity(0.2); // The robot will now move at 20% velocity.
  ROS_INFO_STREAM("Set Joint Velocity to 0.2");

  // Start Gravity Compensation
  my_iiwa_ros_object.getSmartServoService().setJointImpedanceMode(0.0,0.7); // 0.0 for joint stiffness, 0.7 for joint damping
  ROS_INFO_STREAM("Robot set to gravity compensation");


  // Create multithread spinner
  ros::AsyncSpinner spinner(2);
  spinner.start();

  uint64_t start = ros::Time::now().toNSec();

  // Perform loop at 500 Hz
  ros::Rate r(500);
  int ctr = 0;
  while(ros::ok())
  {
    // Print status every second
    if (ctr % 500 == 0)
    {
      my_iiwa_ros_object.getJointPosition(my_joint_position);
      printf("Joint Positions: %f %f %f %f %f %f %f\n",
             my_joint_position.position.a1, my_joint_position.position.a2, my_joint_position.position.a3, my_joint_position.position.a4, my_joint_position.position.a5, my_joint_position.position.a6, my_joint_position.position.a7);
    }
    r.sleep();
  }

  // Turn off gravity compensation and go back to position control mode
  my_iiwa_ros_object.getSmartServoService().setPositionControlMode();
  ROS_INFO_STREAM("Robot set to position control mode");

  return 0;
}

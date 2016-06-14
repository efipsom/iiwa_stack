/** Copyright (C) 2015 Salvatore Virga - salvo.virga@tum.de
 * Technische Universitaet Muenchen
 * Chair for Computer Aided Medical Procedures and Augmented Reality
 * Fakultaet fuer Informatik / I16, Boltzmannstrasse 3, 85748 Garching bei Muenchen, Germany
 * http://campar.in.tum.de
 * 
 * LICENSE :
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Salvatore Virga
 * 
 */

package de.tum.in.camp.kuka.ros;

//ROS imports
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.ros.time.NtpTimeProvider;

import com.kuka.connectivity.motionModel.smartServo.SmartServo;
import com.kuka.roboticsAPI.applicationModel.RoboticsAPIApplication;
import com.kuka.roboticsAPI.deviceModel.LBR;
import com.kuka.roboticsAPI.geometricModel.ObjectFrame;
import com.kuka.roboticsAPI.geometricModel.Tool;
import com.kuka.roboticsAPI.motionModel.controlModeModel.JointImpedanceControlMode;
import com.kuka.roboticsAPI.uiModel.userKeys.IUserKey;
import com.kuka.roboticsAPI.uiModel.userKeys.IUserKeyBar;
import com.kuka.roboticsAPI.uiModel.userKeys.IUserKeyListener;
import com.kuka.roboticsAPI.uiModel.userKeys.UserKeyAlignment;
import com.kuka.roboticsAPI.uiModel.userKeys.UserKeyEvent;

/*
 * This example shows how to monitor the state of the robot, publishing it into ROS nodes.
 * Only the Joint Position of the robot is published in this example,
 * but any other of its property included in the iiwa_msgs ROS package can be published in the same way.
 */
public class ROSMonitor extends RoboticsAPIApplication {

	private LBR robot;
	private Tool tool;
	private String toolFrameID;
	private ObjectFrame toolFrame;
	private SmartServo motion;
	
	private boolean initSuccessful = false;
	private boolean debug = false;
	
	private iiwaPublisher publisher; //< IIWARos Publisher.
	private iiwaConfiguration configuration; //< Configuration via parameters and services.

	// ROS Configuration and Node execution objects.
	private NodeConfiguration nodeConfPublisher;
	private NodeConfiguration nodeConfConfiguration;
	private NodeMainExecutor nodeExecutor;

	// configurable toolbars
	private List<IUserKeyBar> generalKeyBars = new ArrayList<IUserKeyBar>();
	private List<IUserKey> generalKeys = new ArrayList<IUserKey>();
	private List<IUserKeyListener> generalKeyLists = new ArrayList<IUserKeyListener>();
	
	// gravity compensation stuff
	private IUserKeyBar gravcompKeybar;
	private IUserKey gravCompKey;
	private IUserKeyListener gravCompKeyList;
	private boolean gravCompEnabled = false;
	private boolean gravCompSwitched = false;
	int gravityCompDecimationCounter = 0; 
	final int gravityCompDecimation = 10;
	
	public void initialize() {
		robot = getContext().getDeviceFromType(LBR.class);
		
		// standard stuff
		configuration = new iiwaConfiguration();
		publisher = new iiwaPublisher(iiwaConfiguration.getRobotName());
		
		// gravity compensation - only in ROSMonitor for safety
		gravcompKeybar = getApplicationUI().createUserKeyBar("Gravcomp");
		gravCompKeyList = new IUserKeyListener() {
			@Override
			public void onKeyEvent(IUserKey key, com.kuka.roboticsAPI.uiModel.userKeys.UserKeyEvent event) {
				if (event == UserKeyEvent.FirstKeyDown) {
					gravCompEnabled = true;
					gravCompSwitched = true;
				} else if (event == UserKeyEvent.SecondKeyDown) {
					gravCompEnabled = false;
					gravCompSwitched = true;
				}
			}
		};
		gravCompKey = gravcompKeybar.addDoubleUserKey(0, gravCompKeyList, true);
		gravCompKey.setText(UserKeyAlignment.TopMiddle, "ON");
		gravCompKey.setText(UserKeyAlignment.BottomMiddle, "OFF");
		gravcompKeybar.publish();
	
		// ROS initialization
		try {
			URI uri = new URI(iiwaConfiguration.getMasterURI());
			nodeConfConfiguration = NodeConfiguration.newPublic(iiwaConfiguration.getRobotIp());
			nodeConfConfiguration.setTimeProvider(iiwaConfiguration.getTimeProvider());
			nodeConfConfiguration.setNodeName(iiwaConfiguration.getRobotName() + "/iiwa_configuration");
			nodeConfConfiguration.setMasterUri(uri);
			nodeConfPublisher = NodeConfiguration.newPublic(iiwaConfiguration.getRobotIp());
			nodeConfPublisher.setTimeProvider(iiwaConfiguration.getTimeProvider());
			nodeConfPublisher.setNodeName(iiwaConfiguration.getRobotName() + "/iiwa_publisher");
			nodeConfPublisher.setMasterUri(uri);
		}
		catch (Exception e) {
			if (debug) getLogger().info("Node Configuration failed; please check the ROS master IP in the Sunrise app source code");
			getLogger().info(e.toString());
			return;
		}

		try {
			// Start the Publisher node with the set up configuration.
			nodeExecutor = DefaultNodeMainExecutor.newDefault();
			nodeExecutor.execute(publisher, nodeConfPublisher);
			nodeExecutor.execute(configuration, nodeConfConfiguration);
			if (debug) 
				getLogger().info("ROS Node initialized.");
		}
		catch(Exception e) {
			if (debug) 
				getLogger().info("Node Executor failed.");
			
			getLogger().info(e.toString());
			return;
		}
		
		initSuccessful = true;  // we cannot throw here
	}

	public void run() {
		if (!initSuccessful) {
			throw new RuntimeException("Could not init the RoboticApplication successfully");
		}
		
		try {
			configuration.waitForInitialization();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			return;
		}
		
		getLogger().info("using time provider: " + iiwaConfiguration.getTimeProvider().getClass().getSimpleName());

		motion = new SmartServo(robot.getCurrentJointPosition());
		motion.setMinimumTrajectoryExecutionTime(8e-3);
		motion.setJointVelocityRel(configuration.getDefaultRelativeJointSpeed());
		motion.setTimeoutAfterGoalReach(300);
		
		// configurable toolbars to publish events on topics
		configuration.setupToolbars(getApplicationUI(), publisher, generalKeys, generalKeyLists, generalKeyBars);
		
		// Tool to attach
		String toolFromConfig = configuration.getToolName();
		if (toolFromConfig != "") {
			getLogger().info("attaching tool " + toolFromConfig);
			tool = (Tool)getApplicationData().createFromTemplate(toolFromConfig);
			tool.attachTo(robot.getFlange());
			toolFrameID = toolFromConfig + "_link_ee_kuka";
			toolFrame = tool.getFrame("/" + toolFrameID);
		} else {
			getLogger().info("no tool attached");
			toolFrameID = "iiwa_link_ee_kuka";
			toolFrame = robot.getFlange();
		}
		
		// publish joint state?
		publisher.setPublishJointStates(configuration.getPublishJointStates());
		
		if (!SmartServo.validateForImpedanceMode(robot))
			getLogger().error("Too much external torque on the robot! Is it a singular position?");
		
		JointImpedanceControlMode controlMode = new JointImpedanceControlMode(7); // TODO!!
		robot.moveAsync(motion.setMode(controlMode));
		
		// The run loop
		getLogger().info("Starting the ROS Monitor loop...");
		try {
			while(true) { 

				if (iiwaConfiguration.getTimeProvider() instanceof org.ros.time.NtpTimeProvider) {
					((NtpTimeProvider) iiwaConfiguration.getTimeProvider()).updateTime();
				}
				
				/*
				 * This will build a JointPosition message with the current robot state.
				 * Set that message to be published and then publish it if there's a subscriber listening.
				 * Any other of the set methods for iiwa_msgs included in the published can be used at the same time,
				 * one just needs to build the message and set it to the publisher.
				 */
				publisher.publishCurrentState(robot, motion, toolFrame);
				
				if (gravCompEnabled) {
					if (gravCompSwitched) {
						gravCompSwitched = false;
						getLogger().warn("Enabling gravity compensation");
						controlMode.setStiffnessForAllJoints(0);
						controlMode.setDampingForAllJoints(0.7);
						motion.getRuntime().changeControlModeSettings(controlMode);
					}
					
					if ((gravityCompDecimationCounter++ % gravityCompDecimation) == 0)
						motion.getRuntime().setDestination(robot.getCurrentJointPosition());
				} else {
					if (gravCompSwitched) {
						gravCompSwitched = false;
						getLogger().warn("Disabling gravity compensation");
						controlMode.setStiffnessForAllJoints(1500);
						motion.getRuntime().changeControlModeSettings(controlMode);
						motion.getRuntime().setDestination(robot.getCurrentJointPosition());
					}
				}
			} 
		}
		catch (Exception e) {
			getLogger().info("ROS loop aborted. " + e.toString());
		} finally {
			if (nodeExecutor != null) {
				nodeExecutor.shutdownNodeMain(publisher);
				nodeExecutor.shutdownNodeMain(configuration);
				if (debug)getLogger().info("ROS Node terminated.");
			}
			getLogger().info("ROS loop has ended. Application terminated.");
		}
	}

	@Override
	public void dispose() {
		// The Publisher node is killed.
		if (nodeExecutor != null) {
			nodeExecutor.shutdownNodeMain(publisher);
			nodeExecutor.shutdownNodeMain(configuration);
			getLogger().info("ROS nodes have been terminated by Garbage Collection.");
		}
		super.dispose();
	}
}

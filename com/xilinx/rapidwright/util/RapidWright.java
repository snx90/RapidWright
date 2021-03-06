/*
 * 
 * Copyright (c) 2018 Xilinx, Inc. 
 * All rights reserved.
 *
 * Author: Chris Lavin, Xilinx Research Labs.
 *
 * This file is part of RapidWright. 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.xilinx.rapidwright.util;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.python.core.PySystemState;
import org.python.util.jython;

import com.xilinx.rapidwright.device.Device;

/**
 * Main entry point for the RapidWright executable stand-alone jar and 
 * Python (Jython) interactive shell.
 * @author clavin
 *
 */
public class RapidWright {
	/** Option to unpack ./data/ directory into current directory */
	public static final String UNPACK_OPTION_NAME = "--unpack_data";
	/** Option to create JSON Kernel file for Jupyter Notebook support */
	public static final String CREATE_JUPYTER_KERNEL = "--create_jupyter_kernel";
	public static final String HELP_OPTION_NAME = "--help";
	public static final String JUPYTER_KERNEL_FILENAME = "kernel.json";
	
	public static final String[] UNPACK_FOLDERS = new String[]{FileTools.DATA_FOLDER_NAME, FileTools.TCL_FOLDER_NAME, FileTools.IMAGES_FOLDER_NAME};
	
	private static boolean folderCheck(String name){
		if(new File(name).exists()){
			MessageGenerator.briefError("Couldn't unpack ./"+name+"/ directory, file/directory already exists.");
			return false;
		}
		return true;
	}
	
	public static boolean unPackSupportingJarData(){
		for(String folderName : UNPACK_FOLDERS){
			if(!folderCheck(folderName)) return false;
			try{
				CodeSource src = Device.class.getProtectionDomain().getCodeSource();
				if(src == null) {
					MessageGenerator.briefError("Couldn't locate code source domain");
					return false;
				}
				URL jar = src.getLocation();
				ZipInputStream zip = new ZipInputStream(jar.openStream());
				ZipEntry e;
				byte[] buffer = new byte[1024];
				while((e = zip.getNextEntry()) != null){
					String name = e.getName();
					if(name.startsWith(folderName)){
						if(!e.isDirectory()){
							System.out.println("Unpacking " + e.getName());
							File newFile = new File(e.getName());
							new File(newFile.getParent()).mkdirs();
							FileOutputStream fos = new FileOutputStream(newFile);
							
							int len = 0;
							while((len = zip.read(buffer)) > 0){
								fos.write(buffer, 0, len);
							}
							fos.close();
						}
					}
				}
				zip.close();
			} catch(IOException e){
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	private static String toWindowsPath(String linuxPath){
		linuxPath = linuxPath.startsWith("/") ? linuxPath.substring(1) : linuxPath;
		return linuxPath.replace("/", "\\\\");
	}
	
	public static void createJupyterKernelFile(){
		try {
			File f = new File(JUPYTER_KERNEL_FILENAME);
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			bw.write("{\n");
			bw.write(" \"argv\": [\"java\",\n");

			// Figure proper CLASSPATH based on if this is running from a jar or not 
			CodeSource src = RapidWright.class.getProtectionDomain().getCodeSource();
			if(src == null) {
				MessageGenerator.briefError("Couldn't identify classpath for running RapidWright.  "
						+ "Either set the CLASSPATH correctly, or modify " + f.getAbsolutePath() + " "
						+ "to include classpath information");
			}
			bw.write("          \"-classpath\",\n");
			boolean isWindows = FileTools.isWindows();
			String location = src.getLocation().getPath();
			location = isWindows ? toWindowsPath(location) : location;
			if(location.toLowerCase().endsWith(".jar")){
				bw.write("          \""+location+"\",\n");
			}else{
				bw.write("          \""+location+ "");
				File jarDir = new File(location + File.separator + FileTools.JARS_FOLDER_NAME);
				if(jarDir != null && jarDir.isDirectory()){
					for(String jar : jarDir.list()){
						if(isWindows && jar.contains("-linux64-")) continue;
						if(!isWindows && jar.contains("-win64-")) continue;
						if(jar.contains("javadoc")) continue;
						bw.write(";" + jarDir.getAbsolutePath() + File.separator + jar);
					}					
				}else{
					MessageGenerator.briefError("ERROR: Couldn't read "+jarDir.getAbsolutePath()+" directory, please check RapidWright installation.");
				}

				bw.write("\",\n");
			}
			bw.write("          \"org.jupyterkernel.kernel.Session\",\n");
			bw.write("          \"-k\", \"python\",\n");
			bw.write("          \"-f\", \"{connection_file}\"],\n");
			bw.write(" \"display_name\": \"Jython 2.7\",\n");
			bw.write(" \"language\": \"python\"\n");
			bw.write("}\n");
			bw.close();
			System.out.println("Wrote Jupyter Notebook Kernel File: '" + f.getAbsolutePath() + "'\n");
			System.out.println("You can install the RapidWright (Jython 2.7) kernel by running:");
			System.out.println("    $ jupyter kernelspec install " + f.getAbsolutePath().replace(JUPYTER_KERNEL_FILENAME, ""));
			System.out.println("Or control the kernel installation with:");
			System.out.println("    $ jupyter kernelspec list");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		if(args.length == 0){
			// If no arguments, import all major rapidwright packages for ease of use
			@SuppressWarnings("rawtypes")
			Class[] primerClass = new Class[]{
					com.xilinx.rapidwright.debug.DesignInstrumentor.class,
					com.xilinx.rapidwright.design.Cell.class,
					com.xilinx.rapidwright.design.Design.class,
					com.xilinx.rapidwright.design.Module.class,
					com.xilinx.rapidwright.design.ModuleInst.class,
					com.xilinx.rapidwright.design.ModuleCache.class,
					com.xilinx.rapidwright.design.Net.class,
					com.xilinx.rapidwright.design.NetType.class,
					com.xilinx.rapidwright.design.SitePinInst.class,
					com.xilinx.rapidwright.device.PIP.class,
					com.xilinx.rapidwright.design.Port.class,
					com.xilinx.rapidwright.design.PortType.class,
					com.xilinx.rapidwright.design.SiteInst.class,
					com.xilinx.rapidwright.design.blocks.PBlock.class,
					com.xilinx.rapidwright.device.ClockRegion.class,
					com.xilinx.rapidwright.device.Device.class,
					com.xilinx.rapidwright.device.BELClass.class,
					com.xilinx.rapidwright.device.BEL.class,
					com.xilinx.rapidwright.device.FamilyType.class,
					com.xilinx.rapidwright.device.Grade.class,
					com.xilinx.rapidwright.device.IntentCode.class,
					com.xilinx.rapidwright.device.Node.class,
					com.xilinx.rapidwright.device.Package.class,
					com.xilinx.rapidwright.device.Part.class,
					com.xilinx.rapidwright.device.PIPType.class,
					com.xilinx.rapidwright.device.Series.class,
					com.xilinx.rapidwright.device.Site.class,
					com.xilinx.rapidwright.device.SiteTypeEnum.class,
					com.xilinx.rapidwright.device.SLR.class,
					com.xilinx.rapidwright.device.Tile.class,
					com.xilinx.rapidwright.device.TileTypeEnum.class,
					com.xilinx.rapidwright.device.Wire.class,
					com.xilinx.rapidwright.util.Utils.class,
					com.xilinx.rapidwright.device.browser.DeviceBrowser.class,
					com.xilinx.rapidwright.edif.EDIFNetlist.class,
					com.xilinx.rapidwright.edif.EDIFTools.class,
					com.xilinx.rapidwright.examples.AddSubGenerator.class,
					com.xilinx.rapidwright.examples.PolynomialGenerator.class,
					com.xilinx.rapidwright.examples.SLRCrosserGenerator.class,
					com.xilinx.rapidwright.ipi.BlockCreator.class,
					com.xilinx.rapidwright.placer.handplacer.HandPlacer.class,
					com.xilinx.rapidwright.router.Router.class,
					com.xilinx.rapidwright.tests.CodePerfTracker.class,
					com.xilinx.rapidwright.design.Unisim.class,
					com.xilinx.rapidwright.util.FileTools.class,
					com.xilinx.rapidwright.util.DeviceTools.class,
					com.xilinx.rapidwright.device.PartNameTools.class,
					com.xilinx.rapidwright.util.PerformanceExplorer.class,
					com.xilinx.rapidwright.util.StringTools.class,
					com.xilinx.rapidwright.design.DesignTools.class,
					com.xilinx.rapidwright.design.tools.LUTTools.class,
			};
			
			args = new String[3];
			args[0] = "-i";
			args[1] = "-c";
			StringBuilder importCmd = new StringBuilder();
			for(@SuppressWarnings("rawtypes") Class c : primerClass){
				String pkg = c.getPackage().getName();
				importCmd.append("from " + pkg + " import " + c.getSimpleName() + ";");
			}
			args[2] = importCmd.toString();
			System.err.println(Device.FRAMEWORK_NAME + " " + Device.RAPIDWRIGHT_VERSION + " (Jython "+PySystemState.version+")");
		} else {
			for(String s : args){
				if(s.equals(UNPACK_OPTION_NAME)){
					boolean success = unPackSupportingJarData();
					if(success){
						System.out.println("Successfully unpacked "
							+ " RapidWright jar data.  Please set the environment "
							+ "variable RAPIDWRIGHT_PATH to the directory which contains the "
							+ "recently expanded data directory (current directory="+System.getProperty("user.dir")+".");
						return;
					}
					else {
						MessageGenerator.briefErrorAndExit("ERROR: Couldn't unpack ./data directory "
							+ "from RapidWright jar.");
					}
					
				}else if(s.equals(CREATE_JUPYTER_KERNEL)){
					createJupyterKernelFile();
					return;
				}
			}
		}
		
		
		jython.main(args);
	}
}

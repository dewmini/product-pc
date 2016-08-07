/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.pc.core.import_export;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.pc.core.ProcessCenterConstants;
import org.wso2.carbon.pc.core.ProcessCenterException;
import org.wso2.carbon.pc.core.audit.util.RegPermissionUtil;
import org.wso2.carbon.pc.core.internal.ProcessCenterServerHolder;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.UserStoreException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ProcessImport {
    private static final Log log = LogFactory.getLog(ProcessImport.class);
    File[] listOfProcessDirs;
    RegistryService registryService = ProcessCenterServerHolder.getInstance().getRegistryService();
    UserRegistry reg;
    String user;
    private static final String PROCESS_ZIP_DOCUMENTS_DIR = "documents";

    /**
     * @param processZipInputStream
     * @param user
     * @throws IOException
     * @throws RegistryException
     * @throws ProcessCenterException
     * @throws UserStoreException
     */
    public void importProcesses(InputStream processZipInputStream, String user)
            throws IOException, RegistryException, ProcessCenterException, UserStoreException, JSONException {

        if (registryService != null) {
            reg = registryService.getGovernanceUserRegistry(user);
            this.user = user;
            //extract zip file stream to the system disk
            byte[] buffer = new byte[2048];
            ZipInputStream zipInputStream = new ZipInputStream(processZipInputStream);
            new File(ImportExportConstants.IMPORTS_DIR).mkdirs();

            try {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    //counter++;
                    String outpath = ImportExportConstants.IMPORTS_DIR + "/" + entry.getName();
                    String dirPath = outpath.substring(0, outpath.lastIndexOf("/"));
                    new File(dirPath).mkdirs();
                    FileOutputStream fileOutputStream = null;
                    try {
                        fileOutputStream = new FileOutputStream(outpath);
                        int len = 0;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, len);
                        }
                    } finally {
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                        }
                    }
                }
            } finally {
                zipInputStream.close();
            }

            //check if the importing processes are already available
            boolean isProcessesAvailableAlready = isProcessesAlreadyAvailble();
            if (isProcessesAvailableAlready) {
                throw new ProcessCenterException("ALREADY_AVAILABLE") ;
            }

            //else do the process importing for each process
            for (File processDir : listOfProcessDirs) {
                if (processDir.isDirectory()) {
                    String processDirName = processDir.getName();
                    String processDirPath = processDir.getPath();
                    String processRxtPath = processDirPath + "/" + "process_rxt.xml";
                    String processName = processDirName.substring(0, processDirName.lastIndexOf("-"));
                    String processVersion = processDirName
                            .substring(processDirName.lastIndexOf("-") + 1, processDirName.length());
                    putProcessRxt(processName, processVersion, processRxtPath);
                    setImageThumbnail(processName, processVersion, processDirPath);
                    setProcessDocuments(processName, processVersion, processDirPath);
                    setProcessTags(processName, processVersion, processDirPath);
                    setProcessText(processName,processVersion,processDirPath);
                    //set bpmn
                    //set flow chart
                    //Finally remove the Imports folder
                }
            }
        }
    }

    private void setProcessText(String processName, String processVersion, String processDirPath)
            throws IOException, RegistryException {
        Path processTextFilePath = Paths.get(processDirPath + "/" + "process_text.xml");
        if(Files.exists(processTextFilePath)) {
            String processTextFileContent = "";
            Charset charset = Charset.forName("US-ASCII");
            String processAssetPath = "processes/" + processName + "/" + processVersion;
            try (BufferedReader reader = Files.newBufferedReader(processTextFilePath, charset)) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    processTextFileContent += line;
                }
            } catch (IOException e) {
                String errMsg = "Error in reading process tags file";
                throw new IOException(errMsg, e);
            }

            // store process text as a separate resource
            String processTextResourcePath = "processText/" + processName + "/" + processVersion;
            if (processTextFileContent != null && processTextFileContent.length() > 0) {
                Resource processTextResource = reg.newResource();
                processTextResource.setContent(processTextFileContent);
                processTextResource.setMediaType("text/html");
                reg.put(processTextResourcePath, processTextResource);
                reg.addAssociation(processTextResourcePath, processAssetPath, ProcessCenterConstants.ASSOCIATION_TYPE);
            }
        }
    }

    private void setProcessTags(String processName, String processVersion, String processDirPath)
            throws IOException, RegistryException {
        Path processTagsFilePath = Paths.get(processDirPath + "/" + "process_tags.txt");
        if(Files.exists(processTagsFilePath)) {
            String tagsFileContent = "";
            Charset charset = Charset.forName("US-ASCII");
            String processAssetPath = "processes/" + processName + "/" + processVersion;
            try (BufferedReader reader = Files.newBufferedReader(processTagsFilePath, charset)) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    tagsFileContent += line;
                }
            } catch (IOException e) {
                String errMsg = "Error in reading process tags file";
                throw new IOException(errMsg, e);
            }

            String[] tags = tagsFileContent.split("###");
            for (String tag : tags) {
                //tag = tag.trim();
                if (tag.length() > 0) {
                    reg.applyTag(processAssetPath, tag);
                }
            }
        }
    }

    private void setProcessDocuments(String processName, String processVersion, String processDirPath)
            throws ProcessCenterException, JSONException, RegistryException, IOException, SecurityException {

        File docsFolder = new File(processDirPath + "/" + PROCESS_ZIP_DOCUMENTS_DIR);
        if (docsFolder.exists()) {
            File[] docFiles = docsFolder.listFiles();
            for (File docFile : docFiles) {
                if (docFile.isFile()) {
                    String fileName = docFile.getName();

                    String fileExt = FilenameUtils.getExtension(docFile.getPath());
                    String docResourcePath =
                            ProcessCenterConstants.DOC_CONTENT_PATH + processName + "/" + processVersion + "/" +
                                    fileName;
                    Resource docResource = reg.newResource();
                    FileInputStream docFileInputStream = new FileInputStream(docFile);
                    docResource.setContentStream(docFileInputStream);

                    if (fileExt.equalsIgnoreCase("pdf")) {
                        docResource.setMediaType("application/pdf");
                    } else {
                        docResource.setMediaType("application/msword");
                    }
                    String processAssetPath = ProcessCenterConstants.PROCESS_ASSET_ROOT + processName + "/" +
                            processVersion;
                    reg.put(docResourcePath, docResource);
                    reg.addAssociation(docResourcePath, processAssetPath, ProcessCenterConstants.ASSOCIATION_TYPE);
                    docFileInputStream.close();
                }
            }
        }
    }

    /**
     * @param processName
     * @param processVersion
     * @param processDirPath
     * @throws RegistryException
     * @throws IOException
     */
    private void setImageThumbnail(String processName, String processVersion, String processDirPath)
            throws RegistryException, IOException {
        String processAssetPath = ProcessCenterConstants.PROCESS_ASSET_ROOT + processName + "/" +
                processVersion;
        Resource storedProcess = reg.get(processAssetPath);
        String processId = storedProcess.getUUID();

        String imageResourcePath =
                ProcessCenterConstants.PROCESS_ASSET_RESOURCE_REG_PATH + processId + "/images_thumbnail";

        Resource imageContentResource = reg.newResource();

        File imageThumbnailFile = new File(processDirPath + "/" + "process_image_thumbnail");
        byte[] imageContent = Files.readAllBytes(imageThumbnailFile.toPath());
        imageContentResource.setContent(imageContent);
        reg.put(imageResourcePath, imageContentResource);

    }

    /**
     * @return
     * @throws IOException
     * @throws ProcessCenterException
     * @throws RegistryException
     */
    public boolean isProcessesAlreadyAvailble() throws IOException, ProcessCenterException, RegistryException {
        File folder = new File(ImportExportConstants.IMPORTS_DIR);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            if (listOfFiles[0].isDirectory() && listOfFiles.length == 1) {
                String zipHomeDirectoryName = listOfFiles[0].getPath();
                File zipFolder = new File(zipHomeDirectoryName);
                listOfProcessDirs = zipFolder.listFiles();
                ArrayList<String> processListinPC = getProcessList();

                for (File processDir : listOfProcessDirs) {
                    if (processDir.isDirectory()) {
                        String fileName = processDir.getName();
                        if (processListinPC.contains(fileName)) {
                            log.error("Cannot proceed the importing..! Process :" + fileName + "already available in "
                                    + "Process Center" );
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * @return
     * @throws ProcessCenterException
     * @throws RegistryException
     */
    public ArrayList<String> getProcessList() throws ProcessCenterException, RegistryException {

        ArrayList<String> processList = new ArrayList<String>();
        RegistryService registryService = ProcessCenterServerHolder.getInstance().getRegistryService();
        if (registryService != null) {
            UserRegistry reg = registryService.getGovernanceSystemRegistry();

            String[] processPaths = GovernanceUtils.findGovernanceArtifacts("application/vnd.wso2-process+xml", reg);
            for (String processPath : processPaths) {
                String processName = processPath.split("/")[2];
                String processVersion = processPath.split("/")[3];
                processList.add(processName + "-" + processVersion);
            }
        } else {
            String msg = "Registry service not available for retrieving processes.";
            throw new ProcessCenterException(msg);
        }
        return processList;
    }

    /**
     * @param processName
     * @param processVersion
     * @param processRxtPath
     * @throws RegistryException
     * @throws UserStoreException
     * @throws IOException
     */
    public void putProcessRxt(String processName, String processVersion, String processRxtPath)
            throws RegistryException, UserStoreException, IOException {
        File rxtFile = new File(processRxtPath);

        RegPermissionUtil.setPutPermission(registryService, user, ProcessCenterConstants.PROCESS_ASSET_ROOT);
        String processAssetPath = ProcessCenterConstants.PROCESS_ASSET_ROOT + processName + "/" +
                processVersion;
        Resource processRxt = reg.newResource();
        processRxt.setContentStream(FileUtils.openInputStream(rxtFile));
        processRxt.setMediaType("application/vnd.wso2-process+xml");
        reg.put(processAssetPath, processRxt);

    }
}

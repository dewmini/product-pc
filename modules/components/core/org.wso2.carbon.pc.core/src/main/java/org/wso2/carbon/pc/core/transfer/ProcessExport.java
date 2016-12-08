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

package org.wso2.carbon.pc.core.transfer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.carbon.pc.core.ProcessCenterConstants;
import org.wso2.carbon.pc.core.ProcessCenterException;
import org.wso2.carbon.pc.core.assets.resources.ProcessAssociation;
import org.wso2.carbon.pc.core.assets.resources.ProcessDocument;
import org.wso2.carbon.pc.core.assets.resources.Tag;
import org.wso2.carbon.pc.core.internal.ProcessCenterServerHolder;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exporting a process as a zip archive which could be imported into another WSO2 Process Center instance
 */
public class ProcessExport {
    private List<String> exportedProcessList;
    private static final Log log = LogFactory.getLog(ProcessExport.class);

    /**
     * @param processName            name of the process
     * @param processVersion         version of the process
     * @param exportWithAssociations boolean value expressing whether to export with associations or not
     * @param user                   current user
     * @return encoded zip file as a string
     * @throws Exception
     */
    public String initiateExportProcess(String processName, String processVersion, String exportWithAssociations,
            String user) throws Exception {
        exportedProcessList = new ArrayList<String>();
        Path exportsDirPath = Paths.get(ProcessCenterConstants.PROCESS_EXPORT_DIR);
        Files.createDirectories(exportsDirPath);
        Boolean exportWithAssociationsBool = Boolean.valueOf(exportWithAssociations);
        try {
            // save details about the exported zip and the core process
            String exportRootPath =
                    ProcessCenterConstants.PROCESS_EXPORT_DIR + File.separator + processName + "-" + processVersion +
                            ProcessCenterConstants.EXPORTS_DIR_SUFFIX + File.separator;
            Path exportRootDirPath = Paths.get(exportRootPath);
            Files.createDirectories(exportRootDirPath);
            exportProcess(exportRootPath, processName, processVersion, exportWithAssociationsBool, user);

            //zip the folder
            String zipFilePath = ProcessCenterConstants.PROCESS_EXPORT_DIR + File.separator + processName + "-" +
                    processVersion + ProcessCenterConstants.EXPORTS_ZIP_SUFFIX;
            zipFolder(exportRootPath, zipFilePath);
            //encode zip file
            String encodedZip = encodeFileToBase64Binary(zipFilePath);
            //Finally remove the Imports folder and the zip file
            FileUtils.deleteDirectory(new File(ProcessCenterConstants.PROCESS_EXPORT_DIR));
            FileUtils.deleteDirectory(new File(zipFilePath));
            return encodedZip;
        } catch (Exception e) {
            String errMsg = "Failed to export process:" + processName + "-" + processVersion;
            throw new ProcessCenterException(errMsg, e);
        }
    }

    /**
     * Save all the process related artifacts in exportRootPath directory
     *
     * @param exportRootPath         root path of the the destination of exported directory
     * @param processName            process name
     * @param processVersion         process version
     * @param exportWithAssociations boolean value expressing whether to export with associations or not
     * @param user                   current user
     * @throws ProcessCenterException
     */
    public void exportProcess(String exportRootPath, String processName, String processVersion,
            boolean exportWithAssociations, String user) throws ProcessCenterException {

        if (exportedProcessList.contains(processName + "-" + processVersion)) {
            return;
        }
        exportedProcessList.add(processName + "-" + processVersion);

        try {
            RegistryService registryService = ProcessCenterServerHolder.getInstance().getRegistryService();
            if (registryService != null) {
                UserRegistry reg = registryService.getGovernanceUserRegistry(user);
                ProcessDocument processDocument = new ProcessDocument();
                Tag tag = new Tag();

                String exportProcessPath = exportRootPath + processName + "-" + processVersion + File.separator;
                Path exportProcessDocPath = Paths.get(exportProcessPath + ProcessCenterConstants
                        .PROCESS_ZIP_DOCUMENTS_DIR + File.separator);
                Files.createDirectories(exportProcessDocPath);

                //save the process rxt registry entry >> xml
                downloadResource(reg, ProcessCenterConstants.PROCESS_ASSET_ROOT,
                        ProcessCenterConstants.EXPORTED_PROCESS_RXT_FILE, processName, processVersion, "xml",
                        exportProcessPath);
                //save bpmn registry entry >> xml
                downloadResource(reg, ProcessCenterConstants.BPMN_META_DATA_FILE_PATH,
                        ProcessCenterConstants.EXPORTED_BPMN_META_FILE, processName, processVersion, "xml",
                        exportProcessPath);
                //save bpmncontent registry entry >> xml
                downloadResource(reg, ProcessCenterConstants.BPMN_CONTENT_PATH,
                        ProcessCenterConstants.EXPORTED_BPMN_CONTENT_FILE, processName, processVersion, "xml",
                        exportProcessPath);
                //save flowchart registry entry >> json
                downloadResource(reg, ProcessCenterConstants.AUDIT.PROCESS_FLOW_CHART_PATH,
                        ProcessCenterConstants.EXPORTED_FLOW_CHART_FILE, processName, processVersion, "json",
                        exportProcessPath);
                //save processText registry entry
                downloadResource(reg, ProcessCenterConstants.PROCESS_TEXT_PATH,
                        ProcessCenterConstants.EXPORTED_PROCESS_TEXT_FILE, processName, processVersion, "txt",
                        exportProcessPath);

                //save doccontent registry entries >> doc, docx, pdf
                String processResourcePath = ProcessCenterConstants.PROCESS_ASSET_ROOT + processName + File.separator +
                        processVersion;
                JSONArray jsonArray = new JSONArray(processDocument
                        .getUploadedDocumentDetails(ProcessCenterConstants.GREG_PATH + processResourcePath));
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObj = jsonArray.getJSONObject(i);
                    if (jsonObj.getString(ProcessCenterConstants.URL).equals(ProcessCenterConstants.NA)) {
                        String docResourcePath = jsonObj.getString("path");
                        Resource docResource = reg.get(docResourcePath);
                        String[] tempStrArr = docResourcePath.split(File.separator);
                        String docFileName =
                                exportProcessPath + ProcessCenterConstants.PROCESS_ZIP_DOCUMENTS_DIR + File.separator +
                                        tempStrArr[tempStrArr.length - 1];
                        FileOutputStream docFileOutPutStream = new FileOutputStream(docFileName);
                        IOUtils.copy(docResource.getContentStream(), docFileOutPutStream);
                        docFileOutPutStream.close();
                    }
                }

                //download process image thumbnail
                downloadProcessThumbnailImage(reg, processName, processVersion, exportProcessPath);

                //write process tags in a file
                FileOutputStream tagsFileOutputStream = new FileOutputStream(
                        exportProcessPath + ProcessCenterConstants.PROCESS_TAGS_FILE);
                tagsFileOutputStream.write(tag.getProcessTags(processName, processVersion).getBytes());
                tagsFileOutputStream.close();

                //export process associations
                if (exportWithAssociations) {
                    //write details on process associations (sub-processes/predecessors/successors) in a json file
                    FileOutputStream out = new FileOutputStream(
                            exportProcessPath + ProcessCenterConstants.PROCESS_ASSOCIATIONS_FILE);
                    ProcessAssociation processAssociation = new ProcessAssociation();
                    JSONObject successorPredecessorSubprocessListJSON = new JSONObject(processAssociation
                            .getSucessorPredecessorSubprocessList(
                                    ProcessCenterConstants.GREG_PATH + processResourcePath));
                    out.write(successorPredecessorSubprocessListJSON
                            .toString(ProcessCenterConstants.JSON_FILE_INDENT_FACTOR).getBytes());
                    out.close();

                    exportAssociatedProcesses(successorPredecessorSubprocessListJSON, exportRootPath,
                            exportWithAssociations, ProcessCenterConstants.SUBPROCESSES, user);
                    exportAssociatedProcesses(successorPredecessorSubprocessListJSON, exportRootPath,
                            exportWithAssociations, ProcessCenterConstants.PREDECESSORS, user);
                    exportAssociatedProcesses(successorPredecessorSubprocessListJSON, exportRootPath,
                            exportWithAssociations, ProcessCenterConstants.SUCCESSORS, user);
                }
            }
        } catch (Exception e) {
            String errMsg = "Failed to export process:" + processName + "-" + processVersion;
            throw new ProcessCenterException(errMsg, e);
        }
    }

    /**
     * Export associated process (sub process/predecessor/successor) - The related files are saved in the respective
     * directory
     *
     * @param successorPredecessorSubprocessListJSON names of the process's successors, predecessors and subprocesses
     * @param exportRootPath                         root path of the the destination of exported directory
     * @param exportWithAssociations                 boolean value expressing whether to export with associations or not
     * @param assocationType                         process association type
     * @param user                                   current user
     * @throws JSONException
     * @throws ProcessCenterException
     */
    public void exportAssociatedProcesses(JSONObject successorPredecessorSubprocessListJSON, String exportRootPath,
            boolean exportWithAssociations, String assocationType, String user)
            throws JSONException, ProcessCenterException {
        JSONArray associatedProcessesJArray = successorPredecessorSubprocessListJSON.getJSONArray(assocationType);

        for (int i = 0; i < associatedProcessesJArray.length(); i++) {
            String processName = associatedProcessesJArray.getJSONObject(i).getString("name");
            String processVersion = associatedProcessesJArray.getJSONObject(i).getString("version");
            exportProcess(exportRootPath, processName, processVersion, exportWithAssociations, user);
        }
    }

    /**
     * Download non-document process related resources (i.e: flow chart, bpmn, process text) for exporting the process
     *
     * @param reg               UserRegistry
     * @param resourceRoot      process resource root path
     * @param savingFileName    named of the resource file saved
     * @param processName       process name
     * @param processVersion    process version
     * @param exportedFileType  exported file type
     * @param exportProcessPath path of the exported directory
     * @throws RegistryException
     * @throws IOException
     * @throws JSONException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws TransformerException
     */
    public void downloadResource(UserRegistry reg, String resourceRoot, String savingFileName, String processName,
            String processVersion, String exportedFileType, String exportProcessPath)
            throws RegistryException, IOException, JSONException, ParserConfigurationException, SAXException,
            TransformerException {
        String resourcePath = resourceRoot + processName + File.separator + processVersion;
        if (reg.resourceExists(resourcePath)) {
            Resource resource = reg.get(resourcePath);
            FileOutputStream fileOutputStream = new FileOutputStream(exportProcessPath + savingFileName);
            if (exportedFileType.equals("json")) {
                String stringContent = IOUtils
                        .toString(resource.getContentStream(), String.valueOf(StandardCharsets.UTF_8));
                //create JSONObject to pretty-print content
                JSONObject contentInJSON = new JSONObject(stringContent);
                fileOutputStream
                        .write(contentInJSON.toString(ProcessCenterConstants.JSON_FILE_INDENT_FACTOR).getBytes());
                fileOutputStream.close();
            } else if (exportedFileType.equals("xml")) {
                IOUtils.copy(resource.getContentStream(), fileOutputStream);
            } else { //.pdf, doc, docx, txt
                IOUtils.copy(resource.getContentStream(), fileOutputStream);
            }
        }
    }

    /**
     * encode File To Base64Binary format
     *
     * @param fileName name of the file
     * @return encodedString of the file
     * @throws IOException
     */
    public String encodeFileToBase64Binary(String fileName) throws IOException {
        File file = new File(fileName);
        byte[] bytes = loadFile(file);
        byte[] encoded = Base64.encodeBase64(bytes);
        String encodedString = new String(encoded);
        return encodedString;
    }

    /**
     * Load file for the encoding purpose
     *
     * @param file the file to load
     * @return loaded file in bytes
     * @throws IOException
     */
    public static byte[] loadFile(File file) throws IOException {
        byte[] bytes;
        try (InputStream inputStream = new FileInputStream(file)) {
            long length = file.length();
            bytes = new byte[(int) length];
            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead = inputStream.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
            if (offset < bytes.length) {
                throw new IOException("Could not completely read file " + file.getName());
            }
        }
        return bytes;
    }

    /**
     * Download process thumbnail image, for process exporting
     *
     * @param reg               User Registry
     * @param processName       process name
     * @param processVersion    process version
     * @param exportProcessPath path of the exported directory
     * @throws RegistryException
     * @throws IOException
     */
    public void downloadProcessThumbnailImage(UserRegistry reg, String processName, String processVersion,
            String exportProcessPath) throws RegistryException, IOException {
        String processAssetPath = ProcessCenterConstants.PROCESS_ASSET_ROOT + processName + File.separator +
                processVersion;
        Resource storedProcess = reg.get(processAssetPath);
        String processId = storedProcess.getUUID();
        String imageResourcePath = ProcessCenterConstants.PROCESS_ASSET_RESOURCE_REG_PATH + processId + File.separator
                + ProcessCenterConstants.IMAGE_THUMBNAIL;

        if (reg.resourceExists(imageResourcePath)) {
            Resource resource = reg.get(imageResourcePath);
            FileOutputStream fileOutputStream = new FileOutputStream(
                    exportProcessPath + ProcessCenterConstants.IMAGE_THUMBNAIL);
            IOUtils.copy(resource.getContentStream(), fileOutputStream);
            fileOutputStream.close();
        }
    }

    /**
     * Zip the directory
     *
     * @param srcFolder   source directory to zip
     * @param destZipFile destination zip file path
     * @throws Exception
     */
    private void zipFolder(String srcFolder, String destZipFile) throws Exception {
        ZipOutputStream zip = null;
        FileOutputStream fileWriter = null;
        fileWriter = new FileOutputStream(destZipFile);
        zip = new ZipOutputStream(fileWriter);
        addFolderToZip("", srcFolder, zip);
        zip.flush();
        zip.close();
    }

    /**
     * Add a file to the zip archive
     *
     * @param path    path of the zip file
     * @param srcFile source file to zip
     * @param zip     ZipOutputStream to zip the file
     * @throws Exception
     */
    private void addFileToZip(String path, String srcFile, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFile);
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            try (FileInputStream in = new FileInputStream(srcFile)) {
                zip.putNextEntry(new ZipEntry(path + File.separator + folder.getName()));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    /**
     * Add particular directory to the zip
     *
     * @param path      path of the zip file
     * @param srcFolder source folder to zip
     * @param zip       ZipOutputStream to zip the directory
     * @throws Exception
     */
    private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFolder);
        for (String fileName : folder.list()) {
            if (path.equals("")) {
                addFileToZip(folder.getName(), srcFolder + File.separator + fileName, zip);
            } else {
                addFileToZip(path + File.separator + folder.getName(), srcFolder + File.separator + fileName, zip);
            }
        }
    }
}
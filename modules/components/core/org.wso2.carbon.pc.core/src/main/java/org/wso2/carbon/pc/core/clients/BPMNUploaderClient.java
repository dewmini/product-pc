/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.wso2.carbon.pc.core.clients;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.client.Options;
import org.apache.axis2.context.ConfigurationContext;
import org.wso2.carbon.bpmn.core.mgt.model.xsd.UploadedFileItem;
import org.wso2.carbon.bpmn.stub.BPMNUploaderServiceStub;
import org.wso2.carbon.pc.core.ProcessCenterConstants;

import javax.activation.DataHandler;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client handle the process archive uploading
 */
public class BPMNUploaderClient {
    private List<UploadedFileItem> uploadServiceTypeList;
    private BPMNUploaderServiceStub stub;

    /**
     * Initiate uploader client
     *
     * @param ctx
     * @param serverURL
     * @param cookie
     * @throws AxisFault
     */
    public BPMNUploaderClient(ConfigurationContext ctx, String serverURL, String cookie) throws AxisFault {
        String serviceURL = serverURL + "/" + ProcessCenterConstants.SERVICES + "/" + ProcessCenterConstants
                .BPMN_UPLOADER_SERVICE;
        stub = new BPMNUploaderServiceStub(ctx, serviceURL);
        Options options = stub._getServiceClient().getOptions();
        options.setManageSession(true);
        options.setProperty(Constants.Configuration.ENABLE_MTOM, Constants.VALUE_TRUE);
        options.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING, cookie);
        //Increase the time out when sending large attachments
        options.setTimeOutInMilliSeconds(60 * 1000);
        uploadServiceTypeList = new ArrayList<UploadedFileItem>();

    }

    /**
     * Add file item to process server
     *
     * @param dataHandler
     * @param fileName
     * @param fileType
     */
    public void addUploadedFileItem(DataHandler dataHandler, String fileName, String fileType) {
        UploadedFileItem uploadedFileItem = new UploadedFileItem();
        uploadedFileItem.setDataHandler(dataHandler);
        uploadedFileItem.setFileName(fileName);
        uploadedFileItem.setFileType(fileType);
        uploadServiceTypeList.add(uploadedFileItem);
    }

    /**
     * upload file items to Process Server
     *
     * @throws RemoteException
     */
    public void uploadFileItems() throws RemoteException {
        UploadedFileItem[] uploadServiceTypes = new UploadedFileItem[uploadServiceTypeList.size()];
        uploadServiceTypes = uploadServiceTypeList.toArray(uploadServiceTypes);
        stub.uploadService(uploadServiceTypes);
    }
}

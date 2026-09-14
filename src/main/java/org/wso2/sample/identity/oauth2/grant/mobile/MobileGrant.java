/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.sample.identity.oauth2.grant.mobile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.model.RequestParameter;
import org.wso2.carbon.identity.oauth2.token.OAuthTokenReqMessageContext;
import org.wso2.carbon.identity.oauth2.token.handlers.grant.AbstractAuthorizationGrantHandler;
import org.wso2.carbon.user.api.Claim;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom grant type for Identity Server — demonstrates returning custom error codes
 * using IdentityOAuth2Exception.
 *
 * When validateGrant() throws IdentityOAuth2Exception(errorCode, message),
 * the errorCode replaces the default "invalid_grant" in the token error response:
 *   {"error": "<errorCode>", "error_description": "<message>"}
 */
public class MobileGrant extends AbstractAuthorizationGrantHandler {

    private static Log log = LogFactory.getLog(MobileGrant.class);

    public static final String MOBILE_GRANT_PARAM = "mobileNumber";
    private static final String MOBILE_CLAIM_URI = "http://wso2.org/claims/mobile";

    @Override
    public boolean validateGrant(OAuthTokenReqMessageContext oAuthTokenReqMessageContext)
            throws IdentityOAuth2Exception {

        log.info("Mobile Grant handler is hit");

        // Extract request parameters
        RequestParameter[] parameters =
                oAuthTokenReqMessageContext.getOauth2AccessTokenReqDTO().getRequestParameters();

        String mobileNumber = null;

        for (RequestParameter parameter : parameters) {
            if (MOBILE_GRANT_PARAM.equals(parameter.getKey())) {
                if (parameter.getValue() != null && parameter.getValue().length > 0) {
                    mobileNumber = parameter.getValue()[0];
                }
            }
        }

        // Case 1: Mobile number not provided — throw with custom error code
        if (mobileNumber == null || mobileNumber.isEmpty()) {
            throw new IdentityOAuth2Exception("MOBILE_NUMBER_MISSING",
                    "Mobile number is required for this grant type.");
        }

        // Case 2: Invalid mobile number format — throw with custom error code
        if (!isValidMobileNumber(mobileNumber)) {
            throw new IdentityOAuth2Exception("INVALID_MOBILE_FORMAT",
                    "The provided mobile number format is invalid.");
        }

        // Case 3: Look up user by mobile number in the local user store
        String username = findUserByMobileNumber(mobileNumber);

        if (username == null) {
            // No user found with this mobile number — throw with custom error code
            throw new IdentityOAuth2Exception("USER_NOT_FOUND",
                    "No user found with the provided mobile number.");
        }

        log.info("User found for mobile number: " + username);

        AuthenticatedUser authenticatedUser = AuthenticatedUser
                .createLocalAuthenticatedUserFromSubjectIdentifier(username);

        oAuthTokenReqMessageContext.setAuthorizedUser(authenticatedUser);
        oAuthTokenReqMessageContext.setScope(
                oAuthTokenReqMessageContext.getOauth2AccessTokenReqDTO().getScope());

        return true;
    }

    /**
     * Search the local user store for a user whose mobile claim matches the given number.
     *
     * @param mobileNumber the mobile number to search for
     * @return the username if found, null otherwise
     * @throws IdentityOAuth2Exception if an error occurs during the lookup
     */
    private String findUserByMobileNumber(String mobileNumber) throws IdentityOAuth2Exception {

        try {
            UserStoreManager userStoreManager = CarbonContext.getThreadLocalCarbonContext()
                    .getUserRealm().getUserStoreManager();

            // List all users and check their mobile claim
            String[] userList = userStoreManager.listUsers("*", 100);
            if (userList == null) {
                return null;
            }

            for (String user : userList) {
                String userMobile = userStoreManager.getUserClaimValue(user, MOBILE_CLAIM_URI, null);
                if (mobileNumber.equals(userMobile)) {
                    return user;
                }
            }
        } catch (UserStoreException e) {
            log.error("Error while searching user by mobile number", e);
            throw new IdentityOAuth2Exception("USER_STORE_ERROR",
                    "Error occurred while validating the mobile number.");
        }

        return null;
    }

    @Override
    public boolean authorizeAccessDelegation(OAuthTokenReqMessageContext tokReqMsgCtx)
            throws IdentityOAuth2Exception {
        return true;
    }

    @Override
    public boolean validateScope(OAuthTokenReqMessageContext tokReqMsgCtx)
            throws IdentityOAuth2Exception {
        return true;
    }

    private boolean isValidMobileNumber(String mobileNumber) {
        String pattern = "^(\\+\\d{1,3})?\\d{10}$";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(mobileNumber);
        return m.matches();
    }

    @Override
    public boolean isOfTypeApplicationUser() throws IdentityOAuth2Exception {
        return true;
    }
}

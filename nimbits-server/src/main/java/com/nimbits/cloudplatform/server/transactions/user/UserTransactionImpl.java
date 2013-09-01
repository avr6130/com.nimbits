/*
 * Copyright (c) 2013 Nimbits Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package com.nimbits.cloudplatform.server.transactions.user;

import com.google.appengine.api.users.UserServiceFactory;
import com.nimbits.cloudplatform.client.common.Utils;
import com.nimbits.cloudplatform.client.constants.Const;
import com.nimbits.cloudplatform.client.enums.*;
import com.nimbits.cloudplatform.client.model.accesskey.AccessKey;
import com.nimbits.cloudplatform.client.model.accesskey.AccessKeyFactory;
import com.nimbits.cloudplatform.client.model.common.impl.CommonFactory;
import com.nimbits.cloudplatform.client.model.email.EmailAddress;
import com.nimbits.cloudplatform.client.model.entity.Entity;
import com.nimbits.cloudplatform.client.model.entity.EntityModelFactory;
import com.nimbits.cloudplatform.client.model.entity.EntityName;
import com.nimbits.cloudplatform.client.model.user.User;
import com.nimbits.cloudplatform.client.model.user.UserModel;
import com.nimbits.cloudplatform.client.model.user.UserModelFactory;
import com.nimbits.cloudplatform.server.transactions.entity.EntityServiceImpl;
import com.nimbits.cloudplatform.server.transactions.settings.SettingsServiceImpl;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class UserTransactionImpl implements UserTransaction {
    public static final String ERROR1 = "While the current user is authenticated, the email provided does not match " +
            "the authenticated user, so the system is confused and cannot authenticate the request. " +
            "Please report this error.";
    public static final String ERROR2 = "the email that was provided but could not be found. Please log into nimbits with an account " +
            " registered with google at least once.";
    public static final String ERROR3 = "Google User Service Unavailable";
    public static final String AUTHENTICATED_KEY = "AUTHENTICATED_KEY";
    public static final int LIMIT = 1000;
    protected EmailAddress email;

    private com.google.appengine.api.users.UserService googleUserService;
    private User user;
    @Override
    public EmailAddress getEmail() {
        return email;
    }
    @Override
    public User getUser() {
        return user;
    }

    public UserTransactionImpl() {
        googleUserService = UserServiceFactory.getUserService();
    }

    static {

    }

    @Override
    public User getHttpRequestUser(final HttpServletRequest req) {

        // getUserFromCacheBySessionId(req);
//        if (user != null) {
//            return user;
//        }
//        getUserFromSession(req);
//        if (user != null) {
//            putUserInSession(req, user);
//            return user;
//        }

        getEmailFromRequest(req);

//        if (user != null) {
//            putUserInSession(req, user);
//            return user;
//        }
//        getEmailFromSession(req);
        getEmailFromLoggedInUser();

        processAnonRequestWithUUID(req);

        if (email != null) {

            final List<Entity> result = EntityServiceImpl.getEntityByKey(
                    getAdmin(), //avoid infinite recursion
                    email.getValue(), EntityType.user);


            if (result.isEmpty()) {

                if (googleUserService != null) {
                    if (googleUserService.getCurrentUser() != null && googleUserService.getCurrentUser().getEmail().equalsIgnoreCase(email.getValue())) {
                        this.user = createUserRecord(email);
                        this.user.addAccessKey(authenticatedKey(user));
                        //putUserInSession(req, user);
                        return user;
                    } else if (googleUserService.getCurrentUser() != null && !googleUserService.getCurrentUser().getEmail().equalsIgnoreCase(email.getValue())) {
                        throw new SecurityException(ERROR1);
                    } else if (googleUserService.getCurrentUser() == null) {
                        throw new SecurityException(ERROR2);
                    } else {
                        throw new SecurityException(ERROR3);
                    }

                }
                else {
                    throw new SecurityException(ERROR3);
                }


            } else {
                this.user = (User) result.get(0);

                addAccessKeysToUser(req, this.user);

                if (this.user.isRestricted()) {

                    if (googleUserService != null) {
                        if (googleUserService.getCurrentUser() != null
                                && googleUserService.getCurrentUser().getEmail().equalsIgnoreCase(user.getEmail().getValue())) {
                            this.user.addAccessKey(authenticatedKey(user)); //they are logged in
                        }
                    }
                }
                // putUserInSession(req, user);
                return this.user;
            }
        } else {
            throw new SecurityException("There was no account connected to this request");
        }



    }

    @Override
    public void getEmailFromRequest(HttpServletRequest req) {
        if (req != null) {
            String emailParam = req.getParameter(Parameters.email.getText());
            email = Utils.isEmptyString(emailParam) ? null : CommonFactory.createEmailAddress(emailParam);


        }
    }

    @Override
    public AccessKey authenticatedKey(final Entity authenticatedUser) {

        final EntityName name = CommonFactory.createName(AUTHENTICATED_KEY, EntityType.accessKey);
        final Entity en = EntityModelFactory.createEntity(name, "", EntityType.accessKey, ProtectionLevel.onlyMe,  authenticatedUser.getKey(),  authenticatedUser.getKey());
        return AccessKeyFactory.createAccessKey(en, AUTHENTICATED_KEY, authenticatedUser.getKey(), AuthLevel.admin);
    }

    @Override
    public User createUserRecord(final EmailAddress internetAddress) {
        final EntityName name = CommonFactory.createName(internetAddress.getValue(), EntityType.user);
        final Entity entity = EntityModelFactory.createEntity(name, "", EntityType.user, ProtectionLevel.onlyMe,
                name.getValue(), name.getValue(), name.getValue());

        final User newUser = UserModelFactory.createUserModel(entity);
        // newUser.setSecret(UUID.randomUUID().toString());


        return (User) EntityServiceImpl.addUpdateEntity(Arrays.<Entity>asList(newUser)).get(0);


    }

    @Override
    public User getAdmin()   {
        final String adminStr = SettingsServiceImpl.getSetting(SettingType.admin.getName());
        if (Utils.isEmptyString(adminStr)) {
            throw new IllegalArgumentException("Server is missing admin setting!");
        } else {
            final User u = new UserModel();
            u.setName(CommonFactory.createName(adminStr, EntityType.user));
            u.setKey(adminStr);
            u.addAccessKey(createAccessKey(u, AuthLevel.admin));
            u.setParent(adminStr);
            u.setUserAdmin(true);

            return u;
        }
    }

    @Override
    public List<User> getUserByKey(final String key, AuthLevel authLevel)  {
        List<Entity> result = EntityServiceImpl.getEntityByKey(getAnonUser(), key, EntityType.user);
        if (result.isEmpty()) {
            return Collections.emptyList();
        } else {
            User retObj = (User) result.get(0);
            AccessKey k = createAccessKey(retObj, authLevel);
            retObj.addAccessKey(k);

            return Arrays.asList(retObj);
        }

    }

    @Override
    public User getAnonUser() {
        User u = new UserModel();
        try {

            u.setName(CommonFactory.createName(Const.CONST_ANON_EMAIL, EntityType.user));
        } catch (Exception e) {
            return u;
        }


        return u;
    }


    protected void getUserFromCacheBySessionId(HttpServletRequest req) {
        if (req != null) {
            HttpSession session = req.getSession();
            String sessionId;
            sessionId = req.getParameter(Parameters.session.getText());
            if (StringUtils.isEmpty(sessionId)) {
                sessionId = session.getId();

            }
            List<User> sample = UserCache.getCachedAuthenticatedUser(sessionId);
            if (! sample.isEmpty()) {
                this.user =  sample.get(0);
                this.user.setSessionId(sessionId);
            }
        }
    }

    protected void addAccessKeysToUser(HttpServletRequest req, User user) {
        if (req != null && user != null) {
            String accessKey = getAccessKey(req);
            if (!Utils.isEmptyString(accessKey)) {
                //all we have is an email of an existing user, let's see what they can do.
                final Map<String, Entity> keys = EntityServiceImpl.getEntityModelMap(user, EntityType.accessKey, LIMIT);
                for (final Entity k : keys.values()) {
                    if (((AccessKey) k).getCode().equals(accessKey)) {
                        user.addAccessKey((AccessKey) k);
                    }
                }
            }
        }
    }

    protected List<User> getUserFromCache(String accessKey) {
        if (!Utils.isEmptyString(accessKey) && email != null) {

            return UserCache.getCachedAuthenticatedUser(accessKey);

        }
        else {
            return Collections.emptyList();
        }
    }

    protected void getEmailFromLoggedInUser() {
        if (googleUserService != null) {
            if (this.email == null && googleUserService.getCurrentUser() != null) {
                this.email = CommonFactory.createEmailAddress(googleUserService.getCurrentUser().getEmail());
            }
        }
    }

    protected String getAccessKey(HttpServletRequest req) {
        String accessKey = req.getParameter(Parameters.secret.getText());
        if (Utils.isEmptyString(accessKey)) {
            accessKey = req.getParameter(Parameters.key.getText());
        }

        return accessKey;
    }

//    private static void getEmailFromSession(HttpServletRequest req) {
//        if (email == null && req != null) {
//            HttpSession session = req.getSession();
//            if (email == null && session != null && session.getAttribute(Parameters.email.getText()) != null) {
//                email = (EmailAddress) session.getAttribute(Parameters.email.getText());
//            }
//        }
//    }
//    private static void getUserFromSession(HttpServletRequest req) {
//        if (req != null) {
//            HttpSession session = req.getSession();
//            if (session != null && session.getAttribute(Parameters.user.getText()) != null) {
//                user = (User) session.getAttribute(Parameters.user.getText());
//                user.setSessionId(session.getId());
//            }
//        }
//    }

//    private static void putUserInSession(HttpServletRequest req, User user) {
//        if (email == null && req != null) {
//            HttpSession session = req.getSession();
//            if (user != null && session != null) {
//                user.setSessionId(session.getId());
//                session.setAttribute(Parameters.user.getText(), user);
//                UserCache.cacheAuthenticatedUser(session.getId(), user);
//            }
//        }
//    }

    protected void processAnonRequestWithUUID(HttpServletRequest req) {

        if (req != null && this.email == null) {

            String uuid = req.getParameter(Parameters.uuid.getText());
            EntityType entityType = getEntityType(req);

            if (!Utils.isEmptyString(uuid) && this.email == null) { //a request with just a uuid must be public
                List<Entity> anon = EntityServiceImpl.getEntityByUUID(getAnonUser(), uuid, entityType);

                if (!anon.isEmpty()) {
                    Entity anonEntity = anon.get(0);
                    if (anonEntity.getProtectionLevel().equals(ProtectionLevel.everyone)) {
                        this.email = CommonFactory.createEmailAddress(anon.get(0).getOwner());
                    } else {
                        throw new SecurityException("The object you requested was found, but its protection level was set to high to access. Try " +
                                "adding an email address and access key to your request.");
                    }
                }
            }
        }

    }

    protected EntityType getEntityType(HttpServletRequest req) {
        EntityType entityType = null;
        String type = req.getParameter(Parameters.type.getText());
        if (! StringUtils.isEmpty(type)) {
            Integer code = Integer.valueOf(type);
            entityType = EntityType.get(code);

        }
        if (entityType == null) {
            entityType = EntityType.point;
        }
        return entityType;
    }

    protected AccessKey createAccessKey(final Entity u, final AuthLevel authLevel) {

        final Entity en = EntityModelFactory.createEntity(u.getName(), "", EntityType.accessKey, ProtectionLevel.onlyMe,
                u.getName().getValue(), u.getName().getValue());
        return AccessKeyFactory.createAccessKey(en, u.getName().getValue(), u.getName().getValue(), authLevel);

    }







}
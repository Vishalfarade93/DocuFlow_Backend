package com.project.docu.flow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.stereotype.Service;

import com.project.docu.flow.entity.LdapUser;

@Service
public class LdapUserService {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private LdapContextSource contextSource;

    public List<LdapUser> getAllUsers() {
        return ldapTemplate.search(
                "ou=users",
                "(objectClass=inetOrgPerson)",
                (AttributesMapper<LdapUser>) attrs -> {
                    LdapUser user = new LdapUser();
                    user.setUid(getAttributeValue(attrs, "uid"));
                    user.setCn(getAttributeValue(attrs, "cn"));
                    user.setMail(getAttributeValue(attrs, "mail"));
                    return user;
                });
    }

    public List<LdapUser> getUsersByGroup(String groupName) {
        return ldapTemplate.search(
                "ou=groups",
                "(cn=" + groupName + ")",
                (AttributesMapper<List<LdapUser>>) groupAttrs -> {
                    List<LdapUser> users = new ArrayList<>();
                    Attribute members = groupAttrs.get("uniqueMember");
                    if (members != null) {
                        try {
                            NamingEnumeration<?> allMembers = members.getAll();
                            while (allMembers.hasMore()) {
                                String memberDn = (String) allMembers.next();

                                // Perform safe lookup even when baseDn is configured
                                LdapUser user = safeLookup(memberDn);
                                if (user != null) {
                                    users.add(user);
                                }
                            }
                        } catch (NamingException e) {
                            e.printStackTrace();
                        }
                    }
                    return users;
                }).stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public List<String> getEmailsByGroup(String groupName) {
        return getUsersByGroup(groupName).stream()
                .map(LdapUser::getMail)
                .collect(Collectors.toList());
    }

    public String getEmailByUid(String uid) {
        return getAllUsers().stream()
                .filter(u -> uid.equalsIgnoreCase(u.getUid()))
                .map(LdapUser::getMail)
                .findFirst()
                .orElse(null);
    }

    private String getAttributeValue(Attributes attrs, String attrName) throws NamingException {
        Attribute attr = attrs.get(attrName);
        return attr != null ? (String) attr.get() : null;
    }

    private LdapUser safeLookup(String absoluteDn) {
        try {
            LdapName fullDn = new LdapName(absoluteDn);
            LdapName baseDn = contextSource.getBaseLdapName();
            LdapName relativeDn = LdapUtils.removeFirst(fullDn, baseDn);

            return ldapTemplate.lookup(relativeDn, new String[]{"uid", "cn", "mail"},
                    (AttributesMapper<LdapUser>) attrs -> {
                        LdapUser user = new LdapUser();
                        user.setUid(getAttributeValue(attrs, "uid"));
                        user.setCn(getAttributeValue(attrs, "cn"));
                        user.setMail(getAttributeValue(attrs, "mail"));
                        return user;
                    });
        } catch (Exception e) {
            System.out.println("User DN not found or invalid: " + absoluteDn);
            return null;
        }
    }
}

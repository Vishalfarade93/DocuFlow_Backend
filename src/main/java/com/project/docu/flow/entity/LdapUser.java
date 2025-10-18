package com.project.docu.flow.entity;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@RequiredArgsConstructor
public class LdapUser {
	private String uid;
    private String cn;
    private String mail;
}

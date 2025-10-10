package com.project.docu.flow.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.project.docu.flow.document.DocumentContent;

@Repository
public interface DocumentContentRepository extends MongoRepository<DocumentContent, String> {

}
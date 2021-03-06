/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.impl.RevPersonBuilder;
import org.locationtech.geogig.model.impl.RevTagBuilder;
import org.locationtech.geogig.plumbing.CheckRefFormat;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Platform;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Creates a new tag
 * 
 */
public class TagCreateOp extends AbstractGeoGigOp<RevTag> {

    private String name;

    private ObjectId commitId;

    private String message;

    /**
     * Executes the tag creation operation.
     * 
     * @return the created tag
     * 
     */
    @Override
    protected RevTag _call() throws RuntimeException {
        checkState(name != null, "tag name was not provided");
        final String tagRefPath = Ref.TAGS_PREFIX + name;
        checkArgument(!command(RefParse.class).setName(tagRefPath).call().isPresent(),
                "A tag named '" + name + "' already exists.");
        command(CheckRefFormat.class).setThrowsException(true).setRef(tagRefPath).call();

        if (commitId == null) {
            final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
            Preconditions.checkState(currHead.isPresent(),
                    "Repository has no HEAD, can't create tag");
            commitId = currHead.get().getObjectId();
        }
        RevPerson tagger = resolveTagger();
        message = message == null ? "" : message;
        RevTag tag = RevTagBuilder.build(ObjectId.NULL, name, commitId, message, tagger);
        ObjectId id = command(HashObject.class).setObject(tag).call();
        tag = RevTagBuilder.build(id, name, commitId, message, tagger);
        objectDatabase().put(tag);
        Optional<Ref> branchRef = command(UpdateRef.class).setName(tagRefPath)
                .setNewValue(tag.getId()).call();
        checkState(branchRef.isPresent());

        return tag;
    }

    public TagCreateOp setMessage(String message) {
        this.message = message;
        return this;
    }

    public TagCreateOp setCommitId(ObjectId commitId) {
        this.commitId = commitId;
        return this;
    }

    public TagCreateOp setName(String name) {
        this.name = name;
        return this;
    }

    private RevPerson resolveTagger() {
        final String nameKey = "user.name";
        final String emailKey = "user.email";

        Optional<String> name = command(ConfigGet.class).setName(nameKey).call();
        Optional<String> email = command(ConfigGet.class).setName(emailKey).call();

        checkState(name.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                nameKey, nameKey);

        checkState(email.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                emailKey, emailKey);

        String taggerName = name.get();
        String taggerEmail = email.get();

        Platform platform = platform();
        long taggerTimeStamp = platform.currentTimeMillis();
        int taggerTimeZoneOffset = platform.timeZoneOffset(taggerTimeStamp);
        return RevPersonBuilder.build(taggerName, taggerEmail, taggerTimeStamp,
                taggerTimeZoneOffset);
    }

}

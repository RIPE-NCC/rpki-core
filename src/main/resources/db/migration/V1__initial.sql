--
-- Name: ca_lock; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE ca_lock (
    id bigint NOT NULL,
    ca_id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: certificateauthority; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE certificateauthority (
    id bigint NOT NULL,
    type character varying NOT NULL,
    name character varying(2000) NOT NULL,
    owner character varying(2000) NOT NULL,
    last_issued_serial numeric,
    parent_id bigint,
    uuid character varying,
    resources text NOT NULL,
    version bigint NOT NULL,
    random_serial_increment integer,
    down_stream_provisioning_communicator_id bigint,
    identity_certificate character varying(2000),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    last_message_seen_at timestamp without time zone,
    CONSTRAINT hosted_ca_parent_id_check CHECK (CASE WHEN ((type)::text = 'HOSTED'::text) THEN (parent_id IS NOT NULL) WHEN ((type)::text = 'NONHOSTED'::text) THEN (parent_id IS NOT NULL) ELSE (parent_id IS NULL) END)
);


--
-- Name: commandaudit; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE commandaudit (
    id bigint NOT NULL,
    executiontime timestamp without time zone NOT NULL,
    principal text NOT NULL,
    ca_id bigint NOT NULL,
    commandtype text NOT NULL,
    command text NOT NULL,
    ca_version bigint NOT NULL,
    commandgroup text NOT NULL
);


--
-- Name: crlentity; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE crlentity (
    id bigint NOT NULL,
    keypair_id bigint NOT NULL,
    nextnumber bigint NOT NULL,
    encoded bytea NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: down_stream_provisioning_communicator; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE down_stream_provisioning_communicator (
    id bigint NOT NULL,
    encoded_certificate bytea NOT NULL,
    encoded_crl bytea NOT NULL,
    keystore bytea,
    keystore_provider character varying(2000),
    signature_provider character varying(2000) NOT NULL,
    keystore_type character varying(2000),
    last_issued_serial bigint,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: eventaudit; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE eventaudit (
    id bigint NOT NULL,
    executiontime timestamp without time zone NOT NULL,
    principal text NOT NULL,
    ca_id bigint NOT NULL,
    eventtype text NOT NULL,
    event text NOT NULL,
    ca_version bigint NOT NULL
);


--
-- Name: ip_resource_type; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE ip_resource_type (
    id integer NOT NULL,
    name character varying NOT NULL,
    description character varying NOT NULL
);
INSERT INTO ip_resource_type (id, name, description) VALUES (0, 'ASN', 'Autonomous System Number');
INSERT INTO ip_resource_type (id, name, description) VALUES (1, 'IPv4', 'IPv4 Address');
INSERT INTO ip_resource_type (id, name, description) VALUES (2, 'IPv6', 'IPv6 Address');


--
-- Name: keypair; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE keypair (
    id bigint NOT NULL,
    certificateauthority_id bigint NOT NULL,
    name character varying(2000) NOT NULL,
    size integer,
    algorithm character varying(10),
    public_exponent numeric,
    keystore bytea,
    keystore_provider character varying(2000),
    signature_provider character varying(2000),
    keystore_type character varying(2000),
    status character varying(10) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    type character varying NOT NULL,
    remote_public_key character varying,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: manifestentity; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE manifestentity (
    id bigint NOT NULL,
    keypair_id bigint NOT NULL,
    certificate_id bigint NOT NULL,
    nextnumber bigint NOT NULL,
    manifestcms bytea NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: property; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE property (
    id integer NOT NULL,
    key text NOT NULL,
    value text NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: provisioning_audit_log; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE provisioning_audit_log (
    id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    parent_handle character varying(2000) NOT NULL,
    child_handle character varying(2000) NOT NULL,
    request_message_type character varying(100) NOT NULL,
    request_cms_object bytea NOT NULL,
    response_message_type character varying(100),
    response_cms_object bytea,
    response_exception_type character varying(100)
);


--
-- Name: resourcecertificate; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE resourcecertificate (
    id bigint NOT NULL,
    requesting_ca_id bigint,
    issuing_ca_id bigint,
    serial_number numeric NOT NULL,
    subject character varying(2000) NOT NULL,
    subject_public_key bytea NOT NULL,
    issuer character varying(2000) NOT NULL,
    resources text NOT NULL,
    signing_keypair_id bigint,
    validity_not_before timestamp without time zone NOT NULL,
    validity_not_after timestamp without time zone NOT NULL,
    encoded bytea NOT NULL,
    embedded boolean,
    subject_keypair_id bigint,
    publicationuri character varying(2000),
    revocationtime timestamp without time zone,
    type character varying(10) NOT NULL,
    status character varying(10) NOT NULL,
    receiving_ca_id bigint,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: roaentity; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE roaentity (
    id bigint NOT NULL,
    roaspecification_id bigint NOT NULL,
    certificate_id bigint NOT NULL,
    roacms bytea NOT NULL,
    superseded boolean NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: roaspecification; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE roaspecification (
    id bigint NOT NULL,
    certificateauthority_id bigint NOT NULL,
    name character varying(2000) NOT NULL,
    validity_not_before timestamp without time zone,
    validity_not_after timestamp without time zone,
    asn numeric NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: roaspecification_prefixes; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE roaspecification_prefixes (
    roaspecification_id bigint NOT NULL,
    resource_type_id integer NOT NULL,
    resource_start numeric NOT NULL,
    resource_end numeric NOT NULL,
    maximum_length integer
);


--
-- Name: seq_all; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE seq_all
    START WITH 100000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: upstream_request; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE upstream_request (
    id bigint NOT NULL,
    requesting_ca_id bigint NOT NULL,
    upstream_request_xml text NOT NULL
);


--
-- Name: ca_lock_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY ca_lock
    ADD CONSTRAINT ca_lock_pkey PRIMARY KEY (id);


--
-- Name: certificateauthority_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY certificateauthority
    ADD CONSTRAINT certificateauthority_pkey PRIMARY KEY (id);


--
-- Name: commandaudit_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY commandaudit
    ADD CONSTRAINT commandaudit_pkey PRIMARY KEY (id);


--
-- Name: crlentity_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY crlentity
    ADD CONSTRAINT crlentity_pkey PRIMARY KEY (id);


--
-- Name: eventaudit_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY eventaudit
    ADD CONSTRAINT eventaudit_pkey PRIMARY KEY (id);


--
-- Name: identity_material_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY down_stream_provisioning_communicator
    ADD CONSTRAINT identity_material_pkey PRIMARY KEY (id);


--
-- Name: ip_resource_type_name_key; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY ip_resource_type
    ADD CONSTRAINT ip_resource_type_name_key UNIQUE (name);


--
-- Name: ip_resource_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY ip_resource_type
    ADD CONSTRAINT ip_resource_type_pkey PRIMARY KEY (id);


--
-- Name: keypair_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY keypair
    ADD CONSTRAINT keypair_pkey PRIMARY KEY (id);


--
-- Name: manifestentity_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY manifestentity
    ADD CONSTRAINT manifestentity_pkey PRIMARY KEY (id);


--
-- Name: property_key_key; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY property
    ADD CONSTRAINT property_key_key UNIQUE (key);


--
-- Name: property_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY property
    ADD CONSTRAINT property_pkey PRIMARY KEY (id);


--
-- Name: provisioning_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY provisioning_audit_log
    ADD CONSTRAINT provisioning_audit_log_pkey PRIMARY KEY (id);


--
-- Name: resourcecertificate_issuing_ca_id_serial_number_key; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY resourcecertificate
    ADD CONSTRAINT resourcecertificate_issuing_ca_id_serial_number_key UNIQUE (issuing_ca_id, serial_number);


--
-- Name: resourcecertificate_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY resourcecertificate
    ADD CONSTRAINT resourcecertificate_pkey PRIMARY KEY (id);


--
-- Name: roaentity_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY roaentity
    ADD CONSTRAINT roaentity_pkey PRIMARY KEY (id);


--
-- Name: roaspecification_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY roaspecification
    ADD CONSTRAINT roaspecification_pkey PRIMARY KEY (id);


--
-- Name: roaspecification_prefixes_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY roaspecification_prefixes
    ADD CONSTRAINT roaspecification_prefixes_pkey PRIMARY KEY (roaspecification_id, resource_type_id, resource_start, resource_end);


--
-- Name: upstream_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY upstream_request
    ADD CONSTRAINT upstream_request_pkey PRIMARY KEY (id);


--
-- Name: ca_id_key; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE UNIQUE INDEX ca_id_key ON ca_lock USING btree (ca_id);


--
-- Name: certificateauthority_name; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX certificateauthority_name ON certificateauthority USING btree (name);


--
-- Name: certificateauthority_name_key; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE UNIQUE INDEX certificateauthority_name_key ON certificateauthority USING btree (upper((name)::text));


--
-- Name: certificateauthority_parent_id_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX certificateauthority_parent_id_fk ON certificateauthority USING btree (parent_id);


--
-- Name: commandaudit_ca_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX commandaudit_ca_id ON commandaudit USING btree (ca_id);


--
-- Name: crlentity_keypair_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX crlentity_keypair_id ON crlentity USING btree (keypair_id);


--
-- Name: eventaudit_ca_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX eventaudit_ca_id ON eventaudit USING btree (ca_id);


--
-- Name: keypair_ca_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX keypair_ca_id ON keypair USING btree (certificateauthority_id);


--
-- Name: keypair_name_key; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE UNIQUE INDEX keypair_name_key ON keypair USING btree (certificateauthority_id, upper((name)::text));


--
-- Name: manifestentity_certificate_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX manifestentity_certificate_id ON manifestentity USING btree (certificate_id);


--
-- Name: manifestentity_keypair_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX manifestentity_keypair_id ON manifestentity USING btree (keypair_id);


--
-- Name: resourcecertificate_publicationuri_idx; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX resourcecertificate_publicationuri_idx ON resourcecertificate USING btree (publicationuri, serial_number);


--
-- Name: resourcecertificate_requesting_ca_id_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX resourcecertificate_requesting_ca_id_fk ON resourcecertificate USING btree (requesting_ca_id);


--
-- Name: resourcecertificate_signing_keypair_id_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX resourcecertificate_signing_keypair_id_fk ON resourcecertificate USING btree (signing_keypair_id);


--
-- Name: resourcecertificate_subject_keypair_id_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX resourcecertificate_subject_keypair_id_fk ON resourcecertificate USING btree (subject_keypair_id);


--
-- Name: roa_spec_prefix_spec; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX roa_spec_prefix_spec ON roaspecification_prefixes USING btree (roaspecification_id);


--
-- Name: roaentity_cert_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX roaentity_cert_id ON roaentity USING btree (certificate_id);


--
-- Name: roaentity_spec_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX roaentity_spec_id ON roaentity USING btree (roaspecification_id);


--
-- Name: roaspecification_ca_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX roaspecification_ca_id ON roaspecification USING btree (certificateauthority_id);


--
-- Name: roaspecification_name_key; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE UNIQUE INDEX roaspecification_name_key ON roaspecification USING btree (certificateauthority_id, upper((name)::text));


--
-- Name: upstream_request_ca_id; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX upstream_request_ca_id ON upstream_request USING btree (requesting_ca_id);


--
-- Name: certificateauthority_hosted_ca_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY certificateauthority
    ADD CONSTRAINT certificateauthority_hosted_ca_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES certificateauthority(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: crlentity_keypair_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY crlentity
    ADD CONSTRAINT crlentity_keypair_id_fkey FOREIGN KEY (keypair_id) REFERENCES keypair(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: keypair_certificateauthority_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY keypair
    ADD CONSTRAINT keypair_certificateauthority_id_fkey FOREIGN KEY (certificateauthority_id) REFERENCES certificateauthority(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: manifestentity_certificate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY manifestentity
    ADD CONSTRAINT manifestentity_certificate_id_fkey FOREIGN KEY (certificate_id) REFERENCES resourcecertificate(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: manifestentity_keypair_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY manifestentity
    ADD CONSTRAINT manifestentity_keypair_id_fkey FOREIGN KEY (keypair_id) REFERENCES keypair(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: resourcecertificate_issuing_ca_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resourcecertificate
    ADD CONSTRAINT resourcecertificate_issuing_ca_id_fkey FOREIGN KEY (issuing_ca_id) REFERENCES certificateauthority(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: resourcecertificate_receiving_ca_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resourcecertificate
    ADD CONSTRAINT resourcecertificate_receiving_ca_id_fkey FOREIGN KEY (receiving_ca_id) REFERENCES certificateauthority(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: resourcecertificate_requesting_ca_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resourcecertificate
    ADD CONSTRAINT resourcecertificate_requesting_ca_id_fkey FOREIGN KEY (requesting_ca_id) REFERENCES certificateauthority(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: resourcecertificate_signing_keypair_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resourcecertificate
    ADD CONSTRAINT resourcecertificate_signing_keypair_id_fkey FOREIGN KEY (signing_keypair_id) REFERENCES keypair(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: resourcecertificate_subject_keypair_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resourcecertificate
    ADD CONSTRAINT resourcecertificate_subject_keypair_id_fkey FOREIGN KEY (subject_keypair_id) REFERENCES keypair(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: roaentity_certificate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY roaentity
    ADD CONSTRAINT roaentity_certificate_id_fkey FOREIGN KEY (certificate_id) REFERENCES resourcecertificate(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: roaentity_roaspecification_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY roaentity
    ADD CONSTRAINT roaentity_roaspecification_id_fkey FOREIGN KEY (roaspecification_id) REFERENCES roaspecification(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: roaspecification_certificateauthority_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY roaspecification
    ADD CONSTRAINT roaspecification_certificateauthority_id_fkey FOREIGN KEY (certificateauthority_id) REFERENCES certificateauthority(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: roaspecification_prefixes_resource_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY roaspecification_prefixes
    ADD CONSTRAINT roaspecification_prefixes_resource_type_id_fkey FOREIGN KEY (resource_type_id) REFERENCES ip_resource_type(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: roaspecification_prefixes_roaspecification_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY roaspecification_prefixes
    ADD CONSTRAINT roaspecification_prefixes_roaspecification_id_fkey FOREIGN KEY (roaspecification_id) REFERENCES roaspecification(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: upstream_request_requesting_ca_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY upstream_request
    ADD CONSTRAINT upstream_request_requesting_ca_id_fkey FOREIGN KEY (requesting_ca_id) REFERENCES certificateauthority(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: alert_subscription; Type: TABLE; Schema: public; Owner: -; Tablespace:
--

CREATE TABLE alert_subscription (
       created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
       updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    id BIGINT NOT NULL PRIMARY KEY,
    certificateauthority_id BIGINT NOT NULL REFERENCES certificateauthority(id),
    email VARCHAR(200) NOT NULL);

--
-- Name: alert_subscription_certificateauthority_id_fk; Type: INDEX; Schema: public; Owner: -; Tablespace:
--

CREATE UNIQUE INDEX alert_subscription_certificateauthority_id_fk ON alert_subscription (certificateauthority_id);

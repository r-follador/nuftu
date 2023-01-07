package com.genewarrior.nuftu.database;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Date;

@Entity(name = "metadata")
@Table(name = "metadata")
@JsonSerialize(using = Metadata.MetadataSerializer.class)
public class Metadata implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @Column(name = "file")
    @Basic(fetch = FetchType.LAZY, optional = false)
    @Getter
    @Setter
    private byte[] file;

    //350x350 image as preview
    @Column(name = "thumbnail")
    @Basic(fetch = FetchType.LAZY, optional = true)
    @Getter
    @Setter
    private byte[] thumbnail;

    @Column(name = "tokennumber")
    @Getter
    @Setter
    private Long tokennumber;

    @Column(name = "name", length = 255)
    @Getter
    @Setter
    private String name;

    @Column(name = "description", length = 1000)
    @Getter
    @Setter
    private String description;

    @Column(name = "mediatype")
    @Getter
    @Setter
    private String mediatype;

    @Column(name = "creator")
    @Getter
    @Setter
    private String creator;

    @Column(name = "email")
    @Getter
    @Setter
    private String email;

    @Column(name = "sha256")
    @Getter
    @Setter
    private String sha256;

    @Column(name = "session_uuid", unique = true)
    @Getter
    @Setter
    private String uuid;

    @Column(name = "upload_date")
    @Getter
    @Setter
    private Date uploadDate;

    @Column(name = "payment_id")
    @Getter
    @Setter
    private int payment_id;

    @Column(name = "transaction_hash")
    @Getter
    @Setter
    private String transaction_hash;

    public enum TokenState {VALID, MINTING, PAYMENT_WAITING, MINTING_ERROR, LOCKED}

    ;
    @Column(name = "token_state")
    @Getter
    @Setter
    private TokenState tokenState;

    protected static class MetadataSerializer extends JsonSerializer<Metadata> {
        @Override
        public void serialize(Metadata value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("name", value.getName());
            gen.writeStringField("description", value.getDescription());
            gen.writeStringField("externalUrl", "https://nuftu.com/nft/" + value.getTokennumber());

            if (value.getMediatype().startsWith("audio") || value.getMediatype().startsWith("video")) {
                gen.writeStringField("animation_url", "https://nuftu.com/api/file/" + value.getTokennumber());
                gen.writeStringField("image", "https://nuftu.com/api/thumbnail/" + value.getTokennumber());
            } else {
                gen.writeStringField("image", "https://nuftu.com/api/file/" + value.getTokennumber());
            }
            gen.writeEndObject();
        }
    }
}

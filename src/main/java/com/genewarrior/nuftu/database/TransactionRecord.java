package com.genewarrior.nuftu.database;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Date;

@Entity(name = "transactionrecord")
@Table(name = "transactionrecord")
public class TransactionRecord {

    public enum TransactionType {MINT, TRANSFER}

    ;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @Column(name = "eth_http_service")
    @Getter
    @Setter
    String ethHttpService;

    @Column(name = "eth_contractaddress")
    @Getter
    @Setter
    String ethContractaddress;

    @Column(name = "eth_my_adress")
    @Getter
    @Setter
    String ethMyAdress;

    @Column(name = "upload_date")
    @Getter
    @Setter
    private Date uploadDate;

    @Getter
    @Setter
    @Column(name = "transactiontype")
    TransactionType transactionType;

    @Getter
    @Setter
    @Column(name = "gasprice")
    Long gasPrice;

    @Getter
    @Setter
    @Column(name = "gascost")
    Long gasCost;

    @Getter
    @Setter
    @Column(name = "gaslimit")
    Long gaslimit;

    @Getter
    @Setter
    @Column(name = "address")
    String address;

    @Getter
    @Setter
    @Column(name = "privatekey")
    String privateKey;

    @Getter
    @Setter
    @Column(name = "tokenid")
    Long tokenid;

    @Getter
    @Setter
    @Column(name = "receiveraddress")
    String receiveraddress;

    @Getter
    @Setter
    @Column(name = "receipt", columnDefinition = "text")
    String transactionreceipt;

    @Getter
    @Setter
    @Column(name = "transactionhash")
    String transactionhash;


}

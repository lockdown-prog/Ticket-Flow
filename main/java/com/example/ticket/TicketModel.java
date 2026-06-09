package com.example.ticket;

public class TicketModel {
    private int id;
    private String filerName;
    private String subject;
    private String price;
    private String status;

    public TicketModel(String filerName, String subject, String price, String status) {
        this.filerName = filerName;
        this.subject = subject;
        this.price = price;
        this.status = status;
        this.id = -1;
    }

    public TicketModel(int id, String filerName, String subject, String price, String status) {
        this.id = id;
        this.filerName = filerName;
        this.subject = subject;
        this.price = price;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFilerName() { return filerName; }
    public void setFilerName(String filerName) { this.filerName = filerName; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
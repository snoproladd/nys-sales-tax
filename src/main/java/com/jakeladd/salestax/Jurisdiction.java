package com.jakeladd.salestax;

public record Jurisdiction(
    int id, 
    String name, 
    String type, 
    String taxCode, 
    Integer countyId,
    String countyName
) {}
package com.grfriends.payslip.model;

/**
 * Represents one employee's payslip data, parsed from the wage sheet Excel.
 * All wage fields are stored as strings for display — we don't do arithmetic on them.
 */
public class Employee {

    private int slNo;
    private String name;
    private String uan;
    private String esiNo;
    private String designation;
    private String daysWorked;
    private String basicRate;
    private String basicAmount;
    private String da;
    private String washingAllowance;
    private String fuelAllowance;
    private String attendanceAllowance;
    private String foodAllowance;
    private String gratuity;
    private String hra;
    private String overtimeDays;
    private String overtimeAmount;
    private String extraProduction;
    private String performanceIncentive;
    private String otherAllowances;
    private String grossEarnings;
    private String esicSalary;
    private String epfoSalary;
    private String epfDeduction;
    private String esiDeduction;
    private String advanceDeduction;
    private String otherDeductions;
    private String totalDeductions;
    private String netPayable;
    private String phoneNumber;  // filled after matching with contact master
    private String month;        // e.g. "JULY"
    private String year;         // e.g. "2026"
    private String contractorName; // e.g. "FRIENDS ENTERPRISE", "B.P. TRANSPORT"
    private String siteName; // e.g. "GRAPHITE INDIA LIMITED, DURGAPUR - 11", from the PAYSILP sheet

    // --- Getters and Setters ---

    public int getSlNo() { return slNo; }
    public void setSlNo(int slNo) { this.slNo = slNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUan() { return uan; }
    public void setUan(String uan) { this.uan = uan; }

    public String getEsiNo() { return esiNo; }
    public void setEsiNo(String esiNo) { this.esiNo = esiNo; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getDaysWorked() { return daysWorked; }
    public void setDaysWorked(String daysWorked) { this.daysWorked = daysWorked; }

    public String getBasicRate() { return basicRate; }
    public void setBasicRate(String basicRate) { this.basicRate = basicRate; }

    public String getBasicAmount() { return basicAmount; }
    public void setBasicAmount(String basicAmount) { this.basicAmount = basicAmount; }

    public String getDa() { return da; }
    public void setDa(String da) { this.da = da; }

    public String getWashingAllowance() { return washingAllowance; }
    public void setWashingAllowance(String washingAllowance) { this.washingAllowance = washingAllowance; }

    public String getFuelAllowance() { return fuelAllowance; }
    public void setFuelAllowance(String fuelAllowance) { this.fuelAllowance = fuelAllowance; }

    public String getAttendanceAllowance() { return attendanceAllowance; }
    public void setAttendanceAllowance(String attendanceAllowance) { this.attendanceAllowance = attendanceAllowance; }

    public String getFoodAllowance() { return foodAllowance; }
    public void setFoodAllowance(String foodAllowance) { this.foodAllowance = foodAllowance; }

    public String getGratuity() { return gratuity; }
    public void setGratuity(String gratuity) { this.gratuity = gratuity; }

    public String getHra() { return hra; }
    public void setHra(String hra) { this.hra = hra; }

    public String getOvertimeDays() { return overtimeDays; }
    public void setOvertimeDays(String overtimeDays) { this.overtimeDays = overtimeDays; }

    public String getOvertimeAmount() { return overtimeAmount; }
    public void setOvertimeAmount(String overtimeAmount) { this.overtimeAmount = overtimeAmount; }

    public String getExtraProduction() { return extraProduction; }
    public void setExtraProduction(String extraProduction) { this.extraProduction = extraProduction; }

    public String getPerformanceIncentive() { return performanceIncentive; }
    public void setPerformanceIncentive(String performanceIncentive) { this.performanceIncentive = performanceIncentive; }

    public String getOtherAllowances() { return otherAllowances; }
    public void setOtherAllowances(String otherAllowances) { this.otherAllowances = otherAllowances; }

    public String getGrossEarnings() { return grossEarnings; }
    public void setGrossEarnings(String grossEarnings) { this.grossEarnings = grossEarnings; }

    public String getEsicSalary() { return esicSalary; }
    public void setEsicSalary(String esicSalary) { this.esicSalary = esicSalary; }

    public String getEpfoSalary() { return epfoSalary; }
    public void setEpfoSalary(String epfoSalary) { this.epfoSalary = epfoSalary; }

    public String getEpfDeduction() { return epfDeduction; }
    public void setEpfDeduction(String epfDeduction) { this.epfDeduction = epfDeduction; }

    public String getEsiDeduction() { return esiDeduction; }
    public void setEsiDeduction(String esiDeduction) { this.esiDeduction = esiDeduction; }

    public String getAdvanceDeduction() { return advanceDeduction; }
    public void setAdvanceDeduction(String advanceDeduction) { this.advanceDeduction = advanceDeduction; }

    public String getOtherDeductions() { return otherDeductions; }
    public void setOtherDeductions(String otherDeductions) { this.otherDeductions = otherDeductions; }

    public String getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(String totalDeductions) { this.totalDeductions = totalDeductions; }

    public String getNetPayable() { return netPayable; }
    public void setNetPayable(String netPayable) { this.netPayable = netPayable; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getContractorName() { return contractorName; }
    public void setContractorName(String contractorName) { this.contractorName = contractorName; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    /**
     * Whether this employee has a phone number matched from the contact master.
     */
    public boolean hasPhone() {
        return phoneNumber != null && !phoneNumber.isBlank();
    }

    @Override
    public String toString() {
        return String.format("Employee{sl=%d, name='%s', uan='%s', phone='%s', net='%s'}",
                slNo, name, uan, phoneNumber, netPayable);
    }
}

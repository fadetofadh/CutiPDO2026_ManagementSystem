package com.test.cutipdo2026;

import java.io.Serializable;

// 💡 VERY IMPORTANT: Must implement Serializable to prevent pipeline force closes!
public class EmployeeBalance implements Serializable {
    public String name;
    public int cutiBalance;
    public int pdoBalance;
}
package com.qvietdpzai.btnshop;

import java.util.List;

/** 1 mặt hàng trong shop. buy/sell = -1 nghĩa là không giao dịch. */
public final class ShopEntry {
    public String material;
    public double buy = -1;
    public double sell = -1;
    public int stackSize = 0; // >0: bán theo chồng (vd 16)
    public String potion;     // loại potion: "healing", "strong_healing", "long_swiftness"... null nếu không phải

    public ShopEntry() {}

    public ShopEntry(String material, double buy, double sell) {
        this.material = material;
        this.buy = buy;
        this.sell = sell;
    }

    public int buyUnit() { return stackSize > 0 ? stackSize : 1; }

    /** Giá bán 1 đơn vị (theo đồng tiền). */
    public double unitBuy() {
        if (buy < 0) return -1;
        return stackSize > 0 ? buy / stackSize : buy;
    }

    public boolean canBuy() { return buy >= 0; }
    public boolean canSell() { return sell >= 0; }
}

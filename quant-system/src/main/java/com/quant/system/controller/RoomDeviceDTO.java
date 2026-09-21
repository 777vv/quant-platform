package com.quant.system.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 机房设备DTO，自带初始化默认数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDeviceDTO {

    /**
     * 机房编号
     */
    private String roomCode;

    /**
     * 机房名称
     */
    private String roomName;

    /**
     * 设备总数量
     */
    private Integer deviceTotal;

    /**
     * 总功率 kw
     */
    private BigDecimal totalPower;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 设备子列表
     */
    private List<DeviceItemDTO> deviceList;

    /**
     * 静态方法：获取初始化测试数据
     */
    public static RoomDeviceDTO initTestData() {
        return RoomDeviceDTO.builder()
                .roomCode("ROOM-001")
                .roomName("企业主机房")
                .deviceTotal(8)
                .totalPower(new BigDecimal("12.5"))
                .createTime(LocalDateTime.of(2026,9,16,16,30,0))
                .deviceList(DeviceItemDTO.initList())
                .build();
    }

    /**
     * 内嵌子DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceItemDTO{
        private String deviceName;
        private String deviceType;
        private String ipAddr;
        private Integer status; // 0离线 1在线

        public static List<DeviceItemDTO> initList(){
            List<DeviceItemDTO> list = new ArrayList<>();
            list.add(DeviceItemDTO.builder().deviceName("UPS不间断电源").deviceType("POWER").ipAddr("192.168.1.10").status(1).build());
            list.add(DeviceItemDTO.builder().deviceName("核心交换机").deviceType("SWITCH").ipAddr("192.168.1.11").status(1).build());
            list.add(DeviceItemDTO.builder().deviceName("AC无线控制器").deviceType("AC").ipAddr("192.168.1.12").status(1).build());
            return list;
        }
    }
}

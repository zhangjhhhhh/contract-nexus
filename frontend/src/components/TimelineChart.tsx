import * as echarts from "echarts/core";
import { BarChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { SVGRenderer } from "echarts/renderers";
import dayjs from "dayjs";
import { useEffect, useMemo, useRef } from "react";
import type { Contract } from "../types";

echarts.use([BarChart, GridComponent, TooltipComponent, LegendComponent, SVGRenderer]);

type Props = {
  contracts: Contract[];
};

export function TimelineChart({ contracts }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);

  const data = useMemo(() => {
    if (contracts.length === 0) {
      return { months: [], beginCounts: [], endCounts: [] };
    }

    const beginDates: dayjs.Dayjs[] = [];
    const endDates: dayjs.Dayjs[] = [];
    contracts.forEach((c) => {
      const b = dayjs(c.beginTime);
      const e = dayjs(c.endTime);
      if (b.isValid()) beginDates.push(b);
      if (e.isValid()) endDates.push(e);
    });

    if (beginDates.length === 0 && endDates.length === 0) {
      return { months: [], beginCounts: [], endCounts: [] };
    }

    const allDates = [...beginDates, ...endDates];
    let minDate = allDates[0]!.startOf("month");
    let maxDate = allDates[0]!.startOf("month");
    allDates.forEach((d) => {
      const start = d.startOf("month");
      if (start.isBefore(minDate)) minDate = start;
      if (start.isAfter(maxDate)) maxDate = start;
    });

    const monthKeys: string[] = [];
    let cursor = minDate;
    while (cursor.isBefore(maxDate) || cursor.isSame(maxDate, "month")) {
      monthKeys.push(cursor.format("YYYY-MM"));
      cursor = cursor.add(1, "month");
    }

    const displayMonths = monthKeys.map((m) => dayjs(m + "-01").format("YYYY年M月"));

    const beginCounts = monthKeys.map(
      (month) => beginDates.filter((d) => d.format("YYYY-MM") === month).length,
    );
    const endCounts = monthKeys.map(
      (month) => endDates.filter((d) => d.format("YYYY-MM") === month).length,
    );

    return { months: displayMonths, beginCounts, endCounts };
  }, [contracts]);

  useEffect(() => {
    if (!ref.current || data.months.length === 0) return;

    if (!chartRef.current) {
      chartRef.current = echarts.init(ref.current, undefined, { renderer: "svg" });
    }

    const option: echarts.EChartsCoreOption = {
      tooltip: {
        trigger: "axis",
        axisPointer: { type: "shadow" },
      },
      legend: {
        data: ["合同开始", "合同结束"],
        bottom: 0,
        textStyle: { color: "#64748b", fontSize: 13 },
      },
      grid: {
        left: 40,
        right: 20,
        top: 20,
        bottom: 40,
      },
      xAxis: {
        type: "category",
        data: data.months,
        axisLabel: { color: "#64748b", fontSize: 12 },
        axisTick: { alignWithLabel: true },
      },
      yAxis: {
        type: "value",
        name: "合同数量",
        nameTextStyle: { color: "#94a3b8", fontSize: 12 },
        axisLabel: { color: "#94a3b8", fontSize: 12 },
        minInterval: 1,
      },
      series: [
        {
          name: "合同开始",
          type: "bar",
          data: data.beginCounts,
          itemStyle: {
            color: "#3b82f6",
            borderRadius: [4, 4, 0, 0],
          },
          barMaxWidth: 32,
        },
        {
          name: "合同结束",
          type: "bar",
          data: data.endCounts,
          itemStyle: {
            color: "#f59e0b",
            borderRadius: [4, 4, 0, 0],
          },
          barMaxWidth: 32,
        },
      ],
    };

    chartRef.current.setOption(option);
  }, [data]);

  useEffect(() => {
    const handleResize = () => chartRef.current?.resize();
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
      chartRef.current?.dispose();
      chartRef.current = null;
    };
  }, []);

  if (data.months.length === 0) {
    return (
      <div className="flex h-80 items-center justify-center text-muted text-note">
        暂无合同数据
      </div>
    );
  }

  return <div ref={ref} style={{ height: 360 }} />;
}

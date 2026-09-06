import * as echarts from "echarts/core";
import { PieChart } from "echarts/charts";
import { LegendComponent, TooltipComponent } from "echarts/components";
import { SVGRenderer } from "echarts/renderers";
import { useEffect, useRef } from "react";
import type { Contract } from "../types";
import { statusText } from "../utils/format";

echarts.use([PieChart, TooltipComponent, LegendComponent, SVGRenderer]);

type Props = {
  contracts: Contract[];
};

const STATUS_COLORS: Record<string, string> = {
  drafting: "#94a3b8",
  countersigning: "#f59e0b",
  finalizing: "#3b82f6",
  approving: "#f97316",
  signing: "#8b5cf6",
  completed: "#22c55e",
  rejected: "#ef4444",
};

export function StatusPieChart({ contracts }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!ref.current) return;

    if (!chartRef.current) {
      chartRef.current = echarts.init(ref.current, undefined, { renderer: "svg" });
    }

    const distribution = Object.entries(statusText).map(([key, label]) => ({
      key,
      label,
      value: contracts.filter((c) => c.status === key).length,
    }));

    const option: echarts.EChartsCoreOption = {
      tooltip: {
        trigger: "item",
        formatter: "{b}: {c} 份 ({d}%)",
      },
      legend: {
        orient: "vertical",
        right: 0,
        top: "center",
        textStyle: {
          color: "#64748b",
          fontSize: 13,
        },
      },
      series: [
        {
          type: "pie",
          radius: ["45%", "75%"],
          center: ["38%", "50%"],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 4,
            borderColor: "#fff",
            borderWidth: 2,
          },
          label: {
            show: true,
            position: "inside",
            formatter: "{c}",
            fontSize: 14,
            fontWeight: "bold",
            color: "#fff",
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 18,
              fontWeight: "bold",
            },
            scaleSize: 8,
          },
          data: distribution.map((item) => ({
            name: item.label,
            value: item.value,
            itemStyle: { color: STATUS_COLORS[item.key] || "#94a3b8" },
          })),
        },
      ],
    };

    chartRef.current.setOption(option);
  }, [contracts]);

  useEffect(() => {
    const handleResize = () => chartRef.current?.resize();
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
      chartRef.current?.dispose();
      chartRef.current = null;
    };
  }, []);

  return <div ref={ref} style={{ height: 320 }} />;
}

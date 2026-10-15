"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

export interface CountDatum {
  label: string;
  count: number;
}

const BAR_COLOR = "#2563eb";
const ROW_HEIGHT = 40;

/**
 * Groups values into counts, folding anything beyond `limit` categories
 * into a single "Other" row so the chart never invents extra colors or rows.
 */
export function countBy<T>(
  items: T[],
  getLabel: (item: T) => string,
  limit = 8
): CountDatum[] {
  const counts = new Map<string, number>();
  for (const item of items) {
    const label = getLabel(item);
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }

  const sorted = [...counts.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));

  if (sorted.length <= limit) return sorted;

  const visible = sorted.slice(0, limit - 1);
  const otherCount = sorted.slice(limit - 1).reduce((sum, row) => sum + row.count, 0);
  return [...visible, { label: "Other", count: otherCount }];
}

/** Single-series horizontal bar chart of counts, with an accessible table. */
export default function CountBarChart({
  data,
  valueLabel,
}: {
  data: CountDatum[];
  valueLabel: string;
}) {
  const height = Math.max(data.length * ROW_HEIGHT + 24, 120);

  return (
    <>
      <div style={{ height }} aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={data}
            layout="vertical"
            margin={{ top: 4, right: 40, bottom: 4, left: 4 }}
            barCategoryGap={8}
          >
            <CartesianGrid horizontal={false} stroke="#e2e8f0" />
            <XAxis type="number" allowDecimals={false} hide />
            <YAxis
              type="category"
              dataKey="label"
              width={160}
              tickLine={false}
              axisLine={false}
              tick={{ fill: "#475569", fontSize: 12 }}
              tickFormatter={(value: string) =>
                value.length > 22 ? `${value.slice(0, 21)}…` : value
              }
            />
            <Tooltip
              cursor={{ fill: "#f1f5f9" }}
              formatter={(value) => [value, valueLabel]}
              contentStyle={{
                borderRadius: 12,
                borderColor: "#e2e8f0",
                fontSize: 12,
              }}
            />
            <Bar
              dataKey="count"
              fill={BAR_COLOR}
              radius={[0, 4, 4, 0]}
              maxBarSize={22}
              isAnimationActive={false}
            >
              <LabelList
                dataKey="count"
                position="right"
                style={{ fill: "#334155", fontSize: 12, fontWeight: 600 }}
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <table className="sr-only">
        <thead>
          <tr>
            <th scope="col">Category</th>
            <th scope="col">{valueLabel}</th>
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr key={row.label}>
              <td>{row.label}</td>
              <td>{row.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

import type { ReactNode } from "react";
import { ChartNoAxesColumn } from "lucide-react";

import CountBarChart, { type CountDatum } from "@/components/admin/CountBarChart";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/StatePanel";
import type { ApiError } from "@/lib/apiError";

export default function ChartCard({
  title,
  description,
  loading,
  error,
  data,
  valueLabel,
  emptyMessage,
  footnote,
}: {
  title: string;
  description: string;
  loading: boolean;
  error: ApiError | null;
  data: CountDatum[];
  valueLabel: string;
  emptyMessage: string;
  footnote?: ReactNode;
}) {
  return (
    <section className="flex flex-col rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">{title}</h2>
      <p className="mt-0.5 text-sm text-slate-500">{description}</p>

      <div className="mt-4 flex-1">
        {loading ? (
          <LoadingState label="Loading chart data…" />
        ) : error ? (
          <ErrorState error={error} compact />
        ) : data.length === 0 ? (
          <EmptyState icon={ChartNoAxesColumn} title="No data yet" description={emptyMessage} />
        ) : (
          <CountBarChart data={data} valueLabel={valueLabel} />
        )}
      </div>

      {footnote && !loading && !error && data.length > 0 && (
        <p className="mt-3 text-xs text-slate-400">{footnote}</p>
      )}
    </section>
  );
}

import { useState } from 'react'
import { MdOutlineVisibility } from 'react-icons/md'
import DataTable from '../../components/DataTable/DataTable'
import FormField from '../../components/FormField/FormField'
import PageHeader from '../../components/PageHeader/PageHeader'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import * as personService from '../../services/personService'
import { displayOrNT } from '../../utils/format'
import { ROLES } from '../../utils/labels'
import CustomerDetailPanel from './CustomerDetailPanel'
import './CustomersPage.css'

// Clientes y equipos: búsqueda de clientes y su detalle con contacto, equipos y órdenes
export default function CustomersPage() {
  const [search, setSearch] = useState('')
  const [active, setActive] = useState(true)
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState(null)
  const debouncedSearch = useDebouncedValue(search)

  const customers = useRemoteData(
    () => personService.searchPeople({ search: debouncedSearch, role: ROLES.CUSTOMER, active, page }),
    [debouncedSearch, active, page],
  )

  // Clientes nuevos (desde el ingreso) o con contacto corregido en otra pantalla
  useRealtime(TOPICS.PERSONS, ({ payload }) => {
    if (payload.role === ROLES.CUSTOMER) customers.reload({ silent: true })
  })

  const columns = [
    { key: 'document', header: 'Documento', render: (customer) => `${customer.documentType} ${customer.documentNumber}` },
    {
      key: 'fullName',
      header: 'Nombre',
      render: (customer) => (
        <span className="customers-page__name">
          {customer.fullName}
          {!customer.active && <span className="tag">Dado de baja</span>}
        </span>
      ),
    },
    { key: 'phone', header: 'Celular', render: (customer) => displayOrNT(customer.phone) },
    { key: 'email', header: 'Correo', render: (customer) => displayOrNT(customer.email) },
    {
      key: 'actions',
      header: 'Acciones',
      align: 'right',
      render: (customer) => (
        <span className="row-actions">
          <button
            type="button"
            className="action-btn action-btn--view"
            aria-label={`Ver a ${customer.fullName}`}
            onClick={(event) => {
              event.stopPropagation()
              setSelected(customer)
            }}
          >
            <MdOutlineVisibility aria-hidden="true" />
          </button>
        </span>
      ),
    },
  ]

  return (
    <div className="page">
      <PageHeader
        title="Clientes y equipos"
        description="Busque un cliente para ver su contacto, sus equipos y sus órdenes. Los clientes nuevos se registran al recibir un equipo."
      />

      <div className="filters customers-page__filters">
        <FormField id="customers-search" label="Buscar cliente">
          {(props) => (
            <input
              {...props}
              type="search"
              className="form-input"
              placeholder="Nombre, documento, celular o correo"
              value={search}
              onChange={(event) => {
                setSearch(event.target.value)
                setPage(0)
              }}
            />
          )}
        </FormField>
        <FormField id="customers-active" label="Estado">
          {(props) => (
            <select
              {...props}
              className="form-input"
              value={String(active)}
              onChange={(event) => {
                setActive(event.target.value === 'true')
                setPage(0)
              }}
            >
              <option value="true">Activos</option>
              <option value="false">Dados de baja</option>
            </select>
          )}
        </FormField>
      </div>

      <DataTable
        caption="Clientes"
        columns={columns}
        rows={customers.data?.content ?? []}
        loading={customers.loading}
        error={customers.error}
        onRetry={customers.reload}
        emptyMessage={debouncedSearch || !active ? 'No hay clientes con esos filtros.' : 'Aún no hay clientes registrados.'}
        page={customers.data?.page ?? 0}
        totalPages={customers.data?.totalPages ?? 1}
        totalElements={customers.data?.totalElements}
        onPageChange={setPage}
        onRowClick={setSelected}
      />

      {selected && (
        <CustomerDetailPanel
          key={selected.id}
          customer={selected}
          onClose={() => setSelected(null)}
          onUpdated={(updated) => {
            setSelected(updated)
            customers.reload()
          }}
          onDeactivated={() => {
            setSelected(null)
            customers.reload()
          }}
        />
      )}
    </div>
  )
}

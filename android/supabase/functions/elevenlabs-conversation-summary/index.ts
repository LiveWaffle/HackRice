import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Content-Type': 'application/json',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  const url = new URL(req.url)
  const conversationId = url.searchParams.get('conversation_id') ?? url.searchParams.get('conversationId')

  if (!conversationId) {
    return new Response(JSON.stringify({ error: 'Missing conversation_id query parameter.' }), { status: 400, headers: corsHeaders })
  }

  const apiKey = Deno.env.get('ELEVENLABS_API_KEY') ?? Deno.env.get('11LABS')
  if (!apiKey) {
    return new Response(JSON.stringify({ error: 'ELEVENLABS_API_KEY (or 11LABS) is not configured.' }), { status: 500, headers: corsHeaders })
  }

  try {
    const upstream = await fetch(`https://api.elevenlabs.io/v1/convai/conversations/${encodeURIComponent(conversationId)}/summary`, {
      method: 'GET',
      headers: {
        'xi-api-key': apiKey,
        'Content-Type': 'application/json',
      },
    })

    const body = await upstream.text()
    if (!upstream.ok) {
      return new Response(JSON.stringify({ error: 'ElevenLabs summary request failed.', details: body }), { status: upstream.status, headers: corsHeaders })
    }

    const payload = JSON.parse(body)
    return new Response(JSON.stringify(payload), { status: 200, headers: corsHeaders })
  } catch (error) {
    return new Response(JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }), { status: 500, headers: corsHeaders })
  }
})
